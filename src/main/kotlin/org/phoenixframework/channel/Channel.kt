package org.phoenixframework.channel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.phoenixframework.PhoenixResponseCallback
import org.phoenixframework.PhoenixEvent
import org.phoenixframework.PhoenixRequest
import org.phoenixframework.PhoenixResponse
import org.phoenixframework.PhoenixRequestSender
import java.io.IOException
import java.util.ArrayList
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timerTask

class Channel
internal constructor(private val requestSender: PhoenixRequestSender,
    val topic: String, private val objectMapper: ObjectMapper) {

  companion object {
    private const val DEFAULT_REJOIN_INTERVAL: Long = 7000
  }

  private val refBindings = ConcurrentHashMap<String, PhoenixRequest>()
  private val eventBindings = ArrayList<EventBinding>()

  private var state = AtomicReference<ChannelState>(ChannelState.CLOSED)

  private val rejoinTimer = Timer("Rejoin Timer for $topic")
  private var rejoinTimerTask: TimerTask? = null

  /**
   * Initiates a org.phoenixframework.channel join event
   *
   * @return This org.phoenixframework.PhoenixRequest instance
   * @throws IllegalStateException Thrown if the org.phoenixframework.channel has already been joined
   * @throws IOException           Thrown if the join could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun join(payload: String? = null) {
    if (state.get() == ChannelState.JOINED || state.get() == ChannelState.JOINING) {
      throw IllegalStateException(
          "Tried to join multiple times. 'join' can only be invoked once per org.phoenixframework.channel")
    }
    this.state.set(ChannelState.JOINING)
    val joinPayload = payload?.let { objectMapper.readTree(it) }
    pushMessage(PhoenixEvent.JOIN.phxEvent, joinPayload)
        .receive("ok", {
          cancelRejoinTimer()
          this@Channel.state.set(ChannelState.JOINED)
        })
  }

  @Throws(IllegalStateException::class, IOException::class)
  fun leave() {
    if (!canPush()) {
      throw IllegalStateException("Unable to leave org.phoenixframework.channel")
    }
    pushMessage(PhoenixEvent.LEAVE.phxEvent)
  }

  /**
   * Pushes a payload to be sent to the org.phoenixframework.channel
   *
   * @param event   The event name
   * @param payload The message payload
   * @param timeout The number of milliseconds to wait before triggering a timeout
   * @throws IOException           Thrown if the payload cannot be pushed
   * @throws IllegalStateException Thrown if the org.phoenixframework.channel has not yet been joined
   */
  @Throws(IOException::class)
  fun pushRequest(event: String, payload: JsonNode? = null, timeout: Long? = null): PhoenixRequest {
    if (!canPush()) {
      throw IllegalStateException("Unable to push event before org.phoenixframework.channel has been joined")
    }
    return pushMessage(event, payload, timeout)
  }

  /**
   * @param event    The event name
   * @param callback The callback to be invoked with the event's message
   * @return The instance's self
   */
  fun on(event: String, callback: PhoenixResponseCallback): Channel {
    synchronized(eventBindings) {
      this.eventBindings.add(EventBinding(event, callback))
    }
    return this
  }

  fun on(event: PhoenixEvent, callback: PhoenixResponseCallback): Channel = on(event.phxEvent, callback)

  /**
   * Unsubscribe for event notifications
   *
   * @param event The event name
   * @return The instance's self
   */
  fun off(event: String): Channel {
    synchronized(eventBindings) {
      val bindingIter = eventBindings.iterator()
      while (bindingIter.hasNext()) {
        if (bindingIter.next().event == event) {
          bindingIter.remove()
          break
        }
      }
    }
    return this
  }

  /**
   * Triggers event signalling to all callbacks bound to the specified event.
   * Do not call this method except for testing and [Socket].
   *
   * @param triggerEvent The event name
   * @param envelope     The response's envelope relating to the event or null if not relevant.
   */
  internal fun retrieveMessage(response: PhoenixResponse) {
    when (response.event) {
      PhoenixEvent.CLOSE.phxEvent -> {
        clearBindings()
        state.set(ChannelState.CLOSED)
      }
      PhoenixEvent.ERROR.phxEvent -> {
        clearBindings()
        retrieveFailure(response = response)
        startRejoinTimer()
      }
      // Includes org.phoenixframework.PhoenixEvent.REPLY
      else -> {
        response.ref?.let {
          trigger(it, response)
        }
        eventBindings.filter { it.event == response.event }
            .forEach { it.callback?.onResponse(response) }
      }
    }
  }

  internal fun retrieveFailure(throwable: Throwable? = null, response: PhoenixResponse? = null) {
    state.set(ChannelState.ERRORED)
    response?.event.let { event ->
      eventBindings.filter { it.event == event }
          .forEach { it.callback?.onFailure(throwable, response) }
    }
    // TODO(changhee): Rejoin org.phoenixframework.channel with timer.
  }

  private fun trigger(ref: String, response: PhoenixResponse) {
    refBindings[ref]?.matchReceive(response.responseStatus, response)
    refBindings.remove(ref)
  }

  /**
   * @return true if the socket is open and the org.phoenixframework.channel has joined
   */
  private fun canPush(): Boolean {
    return this.state.get() == ChannelState.JOINED && this.requestSender.canPushRequest()
  }

  private fun pushMessage(event: String, payload: JsonNode? = null, timeout: Long? = null)
      : PhoenixRequest {
    val ref = requestSender.makeRef()
    val request = PhoenixRequest(topic, event, payload, ref)
    if (this.requestSender.canPushRequest()) {
      requestSender.pushRequest(request, timeout)
      refBindings[ref] = request
    }
    return request
  }

  private fun rejoin() {
    this@Channel.join()
  }

  private fun startRejoinTimer() {
    rejoinTimerTask = timerTask {
      rejoin()
    }
    rejoinTimer.schedule(rejoinTimerTask, DEFAULT_REJOIN_INTERVAL, DEFAULT_REJOIN_INTERVAL)
  }

  private fun cancelRejoinTimer() {
    rejoinTimerTask?.cancel()
    rejoinTimerTask = null
  }

  private fun clearBindings() {
    eventBindings.clear()
    refBindings.clear()
  }

  override fun toString(): String {
    return "org.phoenixframework.channel.Channel{" +
        "topic='" + topic + '\'' +
        ", eventBindings(" + eventBindings.size + ")=" + eventBindings +
        '}'
  }
}
