package org.phoenixframework.channel

import org.phoenixframework.PhoenixEvent
import org.phoenixframework.Message
import org.phoenixframework.PhoenixMessageSender
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timerTask

class Channel
internal constructor(private val messageSender: PhoenixMessageSender, val topic: String) {

  var rejoinOnFailure: Boolean = false

  private var listeners = mutableSetOf<PhoenixChannelStateListener>()

  private val refBindings = ConcurrentHashMap<String, KeyBinding>()
  private val eventBindings = ConcurrentHashMap<String, KeyBinding>()

  private var joinRef: String? = null
  private var state = AtomicReference<ChannelState>(ChannelState.CLOSED)

  private val rejoinTimer = Timer("Rejoin Timer for $topic")
  private var rejoinTimerTask: TimerTask? = null

  fun registerStateListener(phoenixChannelStateListener: PhoenixChannelStateListener) {
    listeners.add(phoenixChannelStateListener)
  }

  fun unregisterStateListener(phoenixChannelStateListener: PhoenixChannelStateListener) {
    listeners.remove(phoenixChannelStateListener)
  }

  fun clearStateListeners() {
    listeners.clear()
  }

  fun getState(): ChannelState = state.get()

  /**
   * Internal for testing
   */
  internal fun updateState(channelState: ChannelState, throwable: Throwable? = null) {
    state.set(channelState)
    when (channelState) {
      ChannelState.JOINED -> listeners.forEach { it.onJoined(this) }
      ChannelState.CLOSED -> listeners.forEach { it.onClosed(this) }
      ChannelState.CLOSING -> listeners.forEach { it.onClosing(this) }
      ChannelState.ERROR -> listeners.forEach { it.onError(this, throwable) }
      ChannelState.JOINING -> listeners.forEach { it.onJoining(this) }
    }
  }

  /**
   * Initiates a org.phoenixframework.channel join event
   *
   * @param payload Join payload json string for join request
   * @throws IllegalStateException Thrown if the org.phoenixframework.channel has already been joined
   * @throws IOException           Thrown if the join request could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun join(payload: String? = null, success: ((Message?) -> Unit)? = null,
      failure: ((Message?, Throwable?) -> Unit)? = null) {
    if (state.get() == ChannelState.JOINED || state.get() == ChannelState.JOINING) {
      throw IllegalStateException(
          "Tried to join at joined or joining state. 'join' can only be invoked when per org.phoenixframework.channel is not joined or joining.")
    }
    val ref = pushMessage(PhoenixEvent.JOIN.phxEvent, payload)
    ref?.let {
      refBindings[it] = KeyBinding(it, { message: Message? ->
        cancelRejoinTimer()
        joinRef = it
        updateState(ChannelState.JOINED)
        success?.invoke(message) ?: Unit
      }, { message: Message?, t: Throwable? ->
        cancelRejoinTimer()
        updateState(ChannelState.CLOSED)
        failure?.invoke(message, t) ?: Unit
      })
    }
    updateState(ChannelState.JOINING)
  }

  /**
   * Initiates a channel leave event
   *
   * @throws IllegalStateException Thrown if the channel or socket is closed
   * @throws IOException           Thrown if the leave request could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun leave() {
    clearEventBindings()
    if (state.get() == ChannelState.CLOSING) {
      return
    }
    pushMessage(PhoenixEvent.LEAVE.phxEvent)
    state.set(ChannelState.CLOSING)
  }

  /**
   * Pushes a payload to be sent to the channel
   *
   * @return Message
   * @param event   The event name
   * @param payload The message payload
   * @param timeout The number of milliseconds to wait before triggering a timeout
   * @throws IOException           Thrown if the payload cannot be pushed
   * @throws IllegalStateException Thrown if the channel has not yet been joined
   */
  @Throws(IOException::class)
  fun pushRequest(event: String, payload: String? = null, timeout: Long? = null,
      success: ((Message?) -> Unit)? = null,
      failure: ((Message?, Throwable?) -> Unit)? = null) {
    val ref = pushMessage(event, payload, timeout)
    ref?.let { refBindings[it] = KeyBinding(it, success, failure) }
  }

  /**
   * Add callback on the event
   *
   * @param event     The event name string.
   * @param success   Callback on receiving message.
   * @param failure   Callback on failure. If [Message] is not null, its event should be [PhoenixEvent.ERROR].
   * @return The instance's self
   */
  fun on(event: String, success: ((Message?) -> Unit)? = null,
      failure: ((Message?, Throwable?) -> Unit)? = null): Channel {
    eventBindings[event] = KeyBinding(event, success, failure)
    return this
  }

  /**
   * Unsubscribe for event
   *
   * @param event The event name
   * @return The instance's self
   */
  fun off(event: String): Channel {
    eventBindings.remove(event)
    return this
  }

  /**
   * Public for Testing
   * Triggers event signalling to all callbacks bound to the specified event.
   * Do not call this method except for testing and [Socket].
   *
   * @param message Phoenix message of the socket relating to the event or null if not relevant
   */
  fun retrieveMessage(message: Message) {
    when (message.event) {
      PhoenixEvent.CLOSE.phxEvent -> {
        if (joinRef != null && joinRef == message.ref) {
          clearEventBindings()
          updateState(ChannelState.CLOSED)
          joinRef = null
        }
      }
      PhoenixEvent.ERROR.phxEvent -> {
        retrieveFailure(response = message)
      }
    // Includes PhoenixEvent.REPLY
      else -> {
        message.ref?.let {
          trigger(it, message)
        }
        eventBindings[message.event]?.success?.invoke(message)
      }
    }
  }

  internal fun retrieveFailure(response: Message? = null, throwable: Throwable? = null) {
    updateState(ChannelState.ERROR, throwable)
    refBindings.forEach { it.value.failure?.invoke(response, throwable) }
    eventBindings.forEach { it.value.failure?.invoke(response, throwable) }
    clearEventBindings()
    if (messageSender.canSendMessage() && rejoinOnFailure) {
      startRejoinTimer()
    }
  }

  /**
   * Internal for testing
   */
  internal fun trigger(ref: String, message: Message) {
    val binding = refBindings[ref]
    when (message.status) {
      "ok" -> binding?.success?.invoke(message)
      else -> binding?.failure?.invoke(message, null)
    }
    refBindings.remove(ref)
  }

  private fun pushMessage(event: String, payload: String? = null, timeout: Long? = null): String? {
    val state = state.get()!!
    when (state) {
      ChannelState.ERROR,
      ChannelState.CLOSING,
      ChannelState.JOINING -> {
        throwIllegalStateException(state, event, payload)
      }
      ChannelState.CLOSED -> {
        if (event != PhoenixEvent.JOIN.phxEvent) {
          throwIllegalStateException(state, event, payload)
        }
      }
      ChannelState.JOINED -> {
        // does nothing.
      }
    }
    if (!messageSender.canSendMessage()) {
      throw IllegalStateException("Socket is not connected. Message { event: $event, payload: $payload }")
    }
    return messageSender.sendMessage(topic, event, payload, timeout)
  }

  /**
   * Internal for testing
   */
  internal fun startRejoinTimer() {
    rejoinTimerTask = timerTask { join() }
    rejoinTimer.schedule(rejoinTimerTask, DEFAULT_REJOIN_INTERVAL_IN_MILLS, DEFAULT_REJOIN_INTERVAL_IN_MILLS)
  }

  private fun cancelRejoinTimer() {
    rejoinTimerTask?.cancel()
    rejoinTimerTask = null
  }

  private fun clearEventBindings() {
    eventBindings.clear()
  }

  override fun toString(): String {
    return """Channel{
        topic='$topic',
        eventBindings(${eventBindings.size})=" + $eventBindings }
        """
  }

  @Throws(IllegalStateException::class)
  private fun throwIllegalStateException(state: ChannelState, event: String, payload: String?) {
    throw IllegalStateException("Cannot push message on state: ${state.name}. Message { event: $event, payload: $payload }")
  }

  /**
   * Implements test helper methods. Only tests can use below methods.
   */
  internal fun setJoinRef(ref: String) {
    joinRef = ref
  }

  internal fun getJoinRef() = joinRef
  internal fun getRefBindings() = refBindings
  internal fun getEventBindings() = eventBindings


  companion object {

    private const val DEFAULT_REJOIN_INTERVAL_IN_MILLS = 7000L
  }
}
