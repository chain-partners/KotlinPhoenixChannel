package org.phoenixframework.channel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.phoenixframework.PhoenixEvent
import org.phoenixframework.Message
import org.phoenixframework.PhoenixMessageSender
import org.phoenixframework.TestBase

class ChannelTest: TestBase() {

  @RelaxedMockK
  lateinit var messageSender: PhoenixMessageSender

  private lateinit var phxChannel: Channel

  private val topic = "topic"
  private val objectMapper = jacksonObjectMapper()
  private val ref = "ref_1"

  override fun setup() {
    super.setup()
    phxChannel = Channel(messageSender, topic, objectMapper)
    every { messageSender.canSendMessage() } returns true
  }

  @Test
  fun joinTest() {
    every { messageSender.makeRef() } returns ref
    phxChannel.join()

    verify {
      messageSender.sendMessage(
          message = Message(topic, PhoenixEvent.JOIN.phxEvent, null, ref),
          timeout = null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun `cannot join twice`() {
    every { messageSender.makeRef() } returns "ref_1"
    phxChannel.join()
    phxChannel.join()
  }

  @Test
  fun leaveTest() {
    every { messageSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINED)
    phxChannel.leave()

    verify {
      messageSender.sendMessage(
          message = Message(topic, PhoenixEvent.LEAVE.phxEvent, null, ref),
          timeout = null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun leaveErrorTest() {
    every { messageSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINING)
    phxChannel.leave()
  }

  @Test
  fun pushRequest() {
    val event = "test event"
    val payload = "{\"payload\":\"payload\"}"
    val timeout = 10L

    every { messageSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINED)
    phxChannel.pushRequest(event, payload, timeout)

    verify {
      messageSender.sendMessage(
          message = Message(topic, event, objectMapper.readTree(payload), ref),
          timeout = timeout)
    }
  }

  @Test
  fun onTest() {
    val event1 = "event 1"
    val event2 = "event 2"

    runBlocking {
      val job1 = launch {
        phxChannel.on(event1)
      }
      val job2 = launch {
        phxChannel.on(event2)
      }
      job1.join()
      job2.join()

      val eventBindings = phxChannel.getEventBindings()
      assertEquals(2, eventBindings.size)
      assertEquals(event1, eventBindings.find { it.event == event1 }?.event)
      assertEquals(event2, eventBindings.find { it.event == event2 }?.event)
    }
  }

  @Test
  fun offTest() {
    val event1 = "event 1"
    val event2 = "event 2"
    val event3 = "event 3"

    val eventBindings = phxChannel.getEventBindings()
    eventBindings.add(EventBinding(event1, null))
    eventBindings.add(EventBinding(event2, null))
    eventBindings.add(EventBinding(event3, null))

    runBlocking {
      val job1 = launch {
        phxChannel.off(event1)
      }
      val job2 = launch {
        phxChannel.off(event2)
      }
      job1.join()
      job2.join()

      assertEquals(1, eventBindings.size)
      assertEquals(event3, eventBindings[0].event)
    }
  }

  @Test
  fun retrieveCloseResponseTest() {
    val closeMessage = Message(topic, PhoenixEvent.CLOSE.phxEvent, null, null)

    phxChannel.retrieveMessage(closeMessage)
    assertEquals(0, phxChannel.getRefBindings().size)
    assertEquals(0, phxChannel.getEventBindings().size)
    assertEquals(ChannelState.CLOSED, phxChannel.getState())
  }

  @Test
  fun retrieveErrorResponseTest() {
    val errorMessage = Message(topic, PhoenixEvent.ERROR.phxEvent, null, null)
    val spyChannel = spyk(phxChannel)
    spyChannel.retrieveMessage(errorMessage)
    assertEquals(0, phxChannel.getRefBindings().size)
    assertEquals(0, phxChannel.getEventBindings().size)
    verify {
      spyChannel.retrieveFailure(response = errorMessage)
    }
  }

  @Test
  fun retrieveOtherResponseTest() {
    val spyChannel = spyk(phxChannel)
    val testEvent = "test event"
    val eventBindings = phxChannel.getEventBindings()
    val testMessage = Message(topic, testEvent, null, ref)

    val successCallback = spyk({ _: Message? -> })

    val testEventBinding = EventBinding(testEvent, success = successCallback)
    eventBindings.add(testEventBinding)

    spyChannel.retrieveMessage(testMessage)
    verify {
      spyChannel.trigger(ref, testMessage)
      successCallback.invoke(testMessage)
    }
  }

  @Test
  fun retrieveFailureTest() {
    val testThrowable = Exception("Test Exception")
    val spyChannel = spyk(phxChannel)
    val testEvent = "test event"
    val eventBindings = phxChannel.getEventBindings()
    val testMessage = Message(topic, testEvent, null, ref)

    val failureCallback = spyk({ _: Throwable?, _: Message? -> })

    val testEventBinding = EventBinding(testEvent, failure = failureCallback)
    eventBindings.add(testEventBinding)

    spyChannel.retrieveFailure(testThrowable, testMessage)
    assertEquals(ChannelState.ERRORED, spyChannel.getState())
    verify {
      failureCallback.invoke(testThrowable, testMessage)
      spyChannel.startRejoinTimer()
    }
  }

  @Test
  fun triggerTest() {
    val testEvent = "test event"
    val testMessage = Message(topic, testEvent, null, ref)
    val pair = Pair(spyk({ _: Message? -> }), spyk({ _: Message? -> }))

    phxChannel.getRefBindings()[ref] = pair
    assertEquals(1, phxChannel.getRefBindings().size)

    phxChannel.trigger(ref, testMessage)
    verify {
      pair.second.invoke(testMessage)
    }
    assertEquals(0, phxChannel.getRefBindings().size)
  }
}