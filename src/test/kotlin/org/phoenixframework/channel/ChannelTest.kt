package org.phoenixframework.channel

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phoenixframework.PhoenixEvent
import org.phoenixframework.Message
import org.phoenixframework.PhoenixMessageSender
import org.phoenixframework.TestBase
import kotlin.concurrent.thread

class ChannelTest : TestBase() {

  @RelaxedMockK
  private lateinit var messageSender: PhoenixMessageSender

  private lateinit var phxChannel: Channel

  private val topic = "topic"

  override fun setup() {
    super.setup()
    phxChannel = Channel(messageSender, topic)
    every { messageSender.canSendMessage() } returns true
  }

  @Test
  fun defaultState_join_sendJoinEventMessage() {
    phxChannel.join()
    verify {
      messageSender.sendMessage(topic, PhoenixEvent.JOIN.phxEvent, null, null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun join_twice_throwException() {
    phxChannel.join()
    phxChannel.join()
  }

  @Test
  fun joinedChannel_leave_sendLeaveEventMessage() {
    phxChannel.updateState(ChannelState.JOINED)
    phxChannel.leave()

    verify {
      messageSender.sendMessage(topic, PhoenixEvent.LEAVE.phxEvent, null, null)
    }
    assertEquals(ChannelState.CLOSING, phxChannel.getState())
  }

  @Test
  fun stateClosing_leave_doesNothing() {
    phxChannel.updateState(ChannelState.CLOSING)
    phxChannel.leave()

    verify(inverse = true) {
      messageSender.sendMessage(topic, PhoenixEvent.LEAVE.phxEvent, null, null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun stateNotClosingOrJoined_leave_throwError() {
    phxChannel.updateState(ChannelState.JOINING)
    phxChannel.leave()
  }

  @Test
  fun stateJoined_pushRequest_sendMessageAndAddBinding() {
    val event = "test event"
    val payload = "{\"payload\":\"payload\"}"
    val timeout = 10L

    phxChannel.updateState(ChannelState.JOINED)
    every { messageSender.canSendMessage() } returns true
    every { messageSender.sendMessage(any(), any(), any(), any()) } returns "ref"

    phxChannel.pushRequest(event, payload, timeout)

    verify {
      messageSender.sendMessage(topic, event, payload, timeout)
    }
    assertTrue(phxChannel.getRefBindings().keys.contains("ref"))
  }

  @Test(expected = IllegalStateException::class)
  fun stateNotJoined_pushRequest_throwException() {
    val event = "test event"
    val payload = "{\"payload\":\"payload\"}"
    val timeout = 10L

    phxChannel.updateState(ChannelState.CLOSING)
    every { messageSender.canSendMessage() } returns true

    phxChannel.pushRequest(event, payload, timeout)
  }

  @Test
  fun on_addEventBindings() {
    val event1 = "event 1"
    val event2 = "event 2"
    val t1 = thread { phxChannel.on(event1) }
    val t2 = thread { phxChannel.on(event2) }

    t1.run()
    t2.run()

    val eventBindings = phxChannel.getEventBindings()
    assertTrue(eventBindings.keys.containsAll(listOf(event1, event2)))
  }

  @Test
  fun off_removeBindings() {
    val event1 = "event 1"
    val event2 = "event 2"
    val event3 = "event 3"

    val eventBindings = phxChannel.getEventBindings()
    eventBindings[event1] = KeyBinding(event1, null)
    eventBindings[event2] = KeyBinding(event2, null)
    eventBindings[event3] = KeyBinding(event3, null)

    val t1 = thread { phxChannel.off(event1) }
    val t2 = thread { phxChannel.off(event2) }
    t1.run()
    t2.run()

    assertEquals(listOf(event3), eventBindings.keys().toList())
  }

  @Test
  fun closeResponse_retrieveMessage_updateState() {
    phxChannel.updateState(ChannelState.JOINED)
    phxChannel.setJoinRef("join_ref")
    val errorCloseMessage = Message(topic, PhoenixEvent.CLOSE.phxEvent, null, "ref")

    phxChannel.retrieveMessage(errorCloseMessage)
    assertNotEquals(ChannelState.CLOSED, phxChannel.getState())

    val closeMessage = Message(topic, PhoenixEvent.CLOSE.phxEvent, null, "join_ref")
    phxChannel.retrieveMessage(closeMessage)

    assertEquals(0, phxChannel.getRefBindings().size)
    assertEquals(0, phxChannel.getEventBindings().size)
    assertEquals(ChannelState.CLOSED, phxChannel.getState())
    assertNull(phxChannel.getJoinRef())
  }

  @Test
  fun errorResponse_retrieveMessage_updateState() {
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
  fun commonResponse_retrieveMessage_invokeBindings() {
    val spyChannel = spyk(phxChannel)
    val testEvent = "test event"
    val eventBindings = phxChannel.getEventBindings()
    val testMessage = Message(topic, testEvent, null, "ref")

    val successCallback = spyk({ _: Message? -> })

    val testEventBinding = KeyBinding(testEvent, success = successCallback)
    eventBindings[testEvent] = testEventBinding

    spyChannel.retrieveMessage(testMessage)
    verify {
      spyChannel.trigger("ref", testMessage)
      successCallback.invoke(testMessage)
    }
  }

  @Test
  fun retrieveFailure_invokeFailureAction() {
    val testThrowable = Exception("Test Exception")
    val spyChannel = spyk(phxChannel)
    spyChannel.rejoinOnFailure = true
    val testEvent = "test key"
    val eventBindings = phxChannel.getEventBindings()
    val testMessage = Message(topic, testEvent, null, "ref")

    val failureCallback = spyk({ _: Message?, _: Throwable? -> })

    val testEventBinding = KeyBinding(testEvent, failure = failureCallback)
    eventBindings[testEvent] = testEventBinding

    spyChannel.retrieveFailure(testMessage, testThrowable)
    assertEquals(ChannelState.ERROR, spyChannel.getState())
    verify {
      failureCallback.invoke(testMessage, testThrowable)
      spyChannel.startRejoinTimer()
    }
  }

  @Test
  fun trigger_invokeActionOnRef() {
    val testEvent = "test event"
    val testMessage = Message(topic, testEvent, null, "ref")
    val pair = Pair(spyk({ _: Message? -> }), spyk({ _: Message?, _: Throwable? -> }))
    val binding = KeyBinding(testEvent, pair.first, pair.second)
    phxChannel.getRefBindings()["ref"] = binding

    phxChannel.trigger("ref", testMessage)

    verify {
      pair.second.invoke(testMessage, null)
    }
    assertEquals(0, phxChannel.getRefBindings().size)
  }
}