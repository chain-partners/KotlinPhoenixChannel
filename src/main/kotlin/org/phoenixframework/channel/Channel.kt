package org.phoenixframework.channel

import org.phoenixframework.PhoenixEvent
import org.phoenixframework.Message
import org.phoenixframework.PhoenixMessageSender
import org.phoenixframework.socket.PhoenixSocketEventListener
import java.io.IOException
import java.util.ArrayList
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timerTask

class Channel
internal constructor(private val messageSender: PhoenixMessageSender, val topic: String) {

  companion object {
    private const val DEFAULT_REJOIN_INTERVAL: Long = 7000
  }

  var rejoinOnFailure: Boolean = false

  private var listeners = mutableSetOf<PhoenixChannelStateListener>()

  private val refBindings = ConcurrentHashMap<String,
      // Pair<Success, Failure>
      Pair<((Message?) -> Unit)?, ((Message?) -> Unit)?>>()
  private val eventBindings = ArrayList<EventBinding>()

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

  /**
   * Internal for testing
   */
  internal fun setState(channelState: ChannelState) {
    state.set(channelState)
    when (channelState) {
      ChannelState.JOINED -> listeners.forEach { it.onJoined() }
      ChannelState.CLOSED -> listeners.forEach { it.onClosed() }
      ChannelState.ERROR -> listeners.forEach { it.onError() }
      ChannelState.JOINING -> listeners.forEach { it.onJoining() }
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
      failure: ((Message?) -> Unit)? = null) {
    if (state.get() == ChannelState.JOINED || state.get() == ChannelState.JOINING) {
      throw IllegalStateException(
          "Tried to join at joined or joining state. 'join' can only be invoked when per org.phoenixframework.channel is not joined or joining.")
    }
    this@Channel.setState(ChannelState.JOINING)
    val ref = pushMessage(PhoenixEvent.JOIN.phxEvent, payload)
    ref?.let {
      refBindings[it] = Pair({ message: Message? ->
        cancelRejoinTimer()
        joinRef = it
        this@Channel.setState(ChannelState.JOINED)
        success?.invoke(message) ?: Unit
      }, { message: Message? ->
        cancelRejoinTimer()
        this@Channel.setState(ChannelState.CLOSED)
        failure?.invoke(message) ?: Unit
      })
    }
  }

  /**
   * Initiates a org.phoenixframework.channel leave event
   *
   * @throws IllegalStateException Thrown if the org.phoenixframework.channel or org.phoenixframework.socket is closed
   * @throws IOException           Thrown if the leave request could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun leave() {
    clearBindings()
    if (!canPush()) {
      throw IllegalStateException("Unable to leave org.phoenixframework.channel($topic)")
    }
    pushMessage(PhoenixEvent.LEAVE.phxEvent)
  }

  /**
   * Pushes a payload to be sent to the org.phoenixframework.channel
   *
   * @return Message
   * @param event   The event name
   * @param payload The message payload
   * @param timeout The number of milliseconds to wait before triggering a timeout
   * @throws IOException           Thrown if the payload cannot be pushed
   * @throws IllegalStateException Thrown if the org.phoenixframework.channel has not yet been joined
   */
  @Throws(IOException::class)
  fun pushRequest(event: String, payload: String? = null, timeout: Long? = null,
      success: ((Message?) -> Unit)? = null,
      failure: ((Message?) -> Unit)? = null) {
    if (!canPush()) {
      throw IllegalStateException("Unable to push event before org.phoenixframework.channel has been joined")
    }
    val ref = pushMessage(event, payload, timeout)
    ref?.let { refBindings[it] = Pair(success, failure) }
  }

  /**
   * Add callback on the event
   *
   * @param event    The event name string.
   * @param callback The callback to be invoked with the event's message
   * @return The instance's self
   */
  fun on(event: String, success: ((Message?) -> Unit)? = null,
      failure: ((Throwable?, Message?) -> Unit)? = null): Channel {
    synchronized(eventBindings) {
      this.eventBindings.add(EventBinding(event, success, failure))
    }
    return this
  }

  /**
   * Unsubscribe for event
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
          clearBindings()
          this@Channel.setState(ChannelState.CLOSED)
          joinRef = null
        }
      }
      PhoenixEvent.ERROR.phxEvent -> {
        retrieveFailure(response = message)
      }
      // Includes org.phoenixframework.PhoenixEvent.REPLY
      else -> {
        message.ref?.let {
          trigger(it, message)
        }
        eventBindings.filter { it.event == message.event }
            .forEach { it.success?.invoke(message) }
      }
    }
  }

  internal fun retrieveFailure(throwable: Throwable? = null, response: Message? = null) {
    this@Channel.setState(ChannelState.ERROR)
    eventBindings.forEach { it.failure?.invoke(throwable, response) }
    clearBindings()
    if (messageSender.canSendMessage() && rejoinOnFailure) {
      startRejoinTimer()
    }
  }

  /**
   * Internal for testing
   */
  internal fun trigger(ref: String, message: Message) {
    val callbackPair = refBindings[ref]
    when (message.status) {
      "ok" -> callbackPair?.first?.invoke(message)
      else -> callbackPair?.second?.invoke(message)
    }
    refBindings.remove(ref)
  }

  /**
   * public for testing
   * @return true if the socket is open and the org.phoenixframework.channel has joined
   */
  fun canPush(): Boolean {
    return this.state.get() == ChannelState.JOINED && this.messageSender.canSendMessage()
  }

  private fun pushMessage(event: String, payload: String? = null, timeout: Long? = null): String? {
    val ref = messageSender.makeRef()
    if (this.messageSender.canSendMessage()) {
      messageSender.sendMessage(Message(topic, event, payload, ref), timeout)
      return ref
    }
    return null
  }

  /**
   * Internal for testing
   */
  internal fun startRejoinTimer() {
    rejoinTimerTask = timerTask {
      this@Channel.join()
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

  /**
   * Implements test helper methods. Only tests can use below methods.
   */
  internal fun getState() = state.get()
  internal fun setJoinRef(ref: String) {
    joinRef = ref
  }
  internal fun getJoinRef() = joinRef
  internal fun getRefBindings() = refBindings
  internal fun getEventBindings() = eventBindings
}
