package org.phoenixframework.channel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.phoenixframework.PhoenixEvent
import org.phoenixframework.PhoenixRequest
import org.phoenixframework.PhoenixRequestSender
import org.phoenixframework.PhoenixResponse
import org.phoenixframework.PhoenixResponseCallback
import org.phoenixframework.TestBase

class ChannelTest: TestBase() {

  @RelaxedMockK
  lateinit var requestSender: PhoenixRequestSender

  private lateinit var phxChannel: Channel

  private val topic = "topic"
  private val objectMapper = jacksonObjectMapper()
  private val ref = "ref_1"

  override fun setup() {
    super.setup()
    phxChannel = Channel(requestSender, topic, objectMapper)
    every { requestSender.canPushRequest() } returns true
  }

  @Test
  fun joinTest() {
    every { requestSender.makeRef() } returns ref
    phxChannel.join()

    verify {
      requestSender.pushRequest(
          request = PhoenixRequest(topic, PhoenixEvent.JOIN.phxEvent, null, ref),
          timeout = null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun `cannot join twice`() {
    every { requestSender.makeRef() } returns "ref_1"
    phxChannel.join()
    phxChannel.join()
  }

  @Test
  fun leaveTest() {
    every { requestSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINED)
    phxChannel.leave()

    verify {
      requestSender.pushRequest(
          request = PhoenixRequest(topic, PhoenixEvent.LEAVE.phxEvent, null, ref),
          timeout = null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun leaveErrorTest() {
    every { requestSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINING)
    phxChannel.leave()
  }

  @Test
  fun pushRequest() {
    val event = "test event"
    val payload = "{\"payload\":\"payload\"}"
    val timeout = 10L

    every { requestSender.makeRef() } returns ref
    phxChannel.setState(ChannelState.JOINED)
    phxChannel.pushRequest(event, payload, timeout)

    verify {
      requestSender.pushRequest(
          request = PhoenixRequest(topic, event, objectMapper.readTree(payload), ref),
          timeout = timeout)
    }
  }

  @Test
  fun onTest() {
    val event1 = "event 1"
    val event2 = "event 2"
    val responseCallback = object : PhoenixResponseCallback {
      override fun onResponse(response: PhoenixResponse?) {
      }
      override fun onFailure(throwable: Throwable?, response: PhoenixResponse?) {
      }
    }

    runBlocking {
      val job1 = launch {
        phxChannel.on(event1, responseCallback)
      }
      val job2 = launch {
        phxChannel.on(event2, responseCallback)
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
    val closeResponse = PhoenixResponse(topic, PhoenixEvent.CLOSE.phxEvent, null, null)

    phxChannel.retrieveResponse(closeResponse)
    assertEquals(0, phxChannel.getRefBindings().size)
    assertEquals(0, phxChannel.getEventBindings().size)
    assertEquals(ChannelState.CLOSED, phxChannel.getState())
  }

  @Test
  fun retrieveErrorResponseTest() {
    val errorResponse = PhoenixResponse(topic, PhoenixEvent.ERROR.phxEvent, null, null)
    val spyChannel = spyk(phxChannel)
    spyChannel.retrieveResponse(errorResponse)
    assertEquals(0, phxChannel.getRefBindings().size)
    assertEquals(0, phxChannel.getEventBindings().size)
    verify {
      spyChannel.retrieveFailure(response = errorResponse)
    }
  }

  @Test
  fun retrieveOtherResponseTest() {
    val spyChannel = spyk(phxChannel)
    val testEvent = "test event"
    val eventBindings = phxChannel.getEventBindings()
    val testResponse = PhoenixResponse(topic, testEvent, null, ref)

    val spyCallback = spyk(object : PhoenixResponseCallback {
      override fun onResponse(response: PhoenixResponse?) {
      }
      override fun onFailure(throwable: Throwable?, response: PhoenixResponse?) {
      }
    })
    val testEventBinding = EventBinding(testEvent, spyCallback)
    eventBindings.add(testEventBinding)

    spyChannel.retrieveResponse(testResponse)
    verify {
      spyChannel.trigger(ref, testResponse)
      spyCallback.onResponse(response = testResponse)
    }
  }

  @Test
  fun retrieveFailureTest() {
    val testThrowable = Exception("Test Exception")
    val spyChannel = spyk(phxChannel)
    val testEvent = "test event"
    val eventBindings = phxChannel.getEventBindings()
    val testResponse = PhoenixResponse(topic, testEvent, null, ref)

    val spyCallback = spyk(object : PhoenixResponseCallback {
      override fun onResponse(response: PhoenixResponse?) {
      }
      override fun onFailure(throwable: Throwable?, response: PhoenixResponse?) {
      }
    })
    val testEventBinding = EventBinding(testEvent, spyCallback)
    eventBindings.add(testEventBinding)

    spyChannel.retrieveFailure(testThrowable, testResponse)
    assertEquals(ChannelState.ERRORED, spyChannel.getState())
    verify {
      spyCallback.onFailure(testThrowable, testResponse)
      spyChannel.startRejoinTimer()
    }
  }

  @Test
  fun triggerTest() {
    val testEvent = "test event"
    val testResponse = PhoenixResponse(topic, testEvent, null, ref)
    val spyRequest = spyk(PhoenixRequest(topic))

    phxChannel.getRefBindings()[ref] = spyRequest
    assertEquals(1, phxChannel.getRefBindings().size)

    phxChannel.trigger(ref, testResponse)
    assertEquals(0, phxChannel.getRefBindings().size)
    verify {
      spyRequest.matchReceive(any(), response = testResponse)
    }
  }
}