import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.ArrayList

class Channel
internal constructor(private val pushDelegate: PhoenixRequestSender, val topic: String, private val objectMapper: ObjectMapper) {
  private val bindings = ArrayList<Binding>()

  private var state = ChannelState.CLOSED

  private fun pushMessage(event: String, payload: JsonNode? = null, timeout: Long? = null, callback: MessageCallback? = null) {
    val request = PhoenixRequest(topic, event, payload)
    pushDelegate.pushMessage(request, timeout, callback)
    callback?.let {
      synchronized(bindings) {
        bindings.add(Binding(event, callback))
      }
    }
  }

  /**
   * Initiates a channel join event
   *
   * @return This PhoenixRequest instance
   * @throws IllegalStateException Thrown if the channel has already been joined
   * @throws IOException           Thrown if the join could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun join(payload: String?, callback: MessageCallback) {
    if (state == ChannelState.JOINED || state == ChannelState.JOINING) {
      throw IllegalStateException(
          "Tried to join multiple times. 'join' can only be invoked once per channel")
    }
    val joinPayload = objectMapper.readTree(payload)
    this.state = ChannelState.JOINING
    pushMessage(PhoenixEvent.JOIN.phxEvent, joinPayload, callback = callback)
  }

  /**
   * Triggers event signalling to all callbacks bound to the specified event.
   * Do not call this method except for testing and [Socket].
   *
   * @param triggerEvent The event name
   * @param envelope     The phoenixResponse's envelope relating to the event or null if not relevant.
   */
  internal fun retrieveMessage(phoenixResponse: PhoenixResponse) {
    when (phoenixResponse.event) {
      PhoenixEvent.JOIN.phxEvent -> {
        state = ChannelState.JOINED
      }
      PhoenixEvent.CLOSE.phxEvent -> {
        state = ChannelState.CLOSED
      }
      PhoenixEvent.ERROR.phxEvent -> {
        retrieveFailure(response = phoenixResponse)
      }
      // Includes PhoenixEvent.REPLY
      else -> {
        bindings.filter { it.event == phoenixResponse.event }
            .forEach { it.callback?.onMessage("ok", phoenixResponse) }
      }
    }
  }

  internal fun retrieveFailure(throwable: Throwable? = null, response: PhoenixResponse? = null) {
    state = ChannelState.ERRORED
    response?.event.let { event ->
      bindings.filter { it.event == event }
          .forEach { it.callback?.onFailure(throwable, response) }
    }
    // TODO(changhee): Rejoin channel with timer.
  }

  /**
   * @return true if the socket is open and the channel has joined
   */
  private fun canPush(): Boolean {
    return this.state === ChannelState.JOINED && this.pushDelegate.canPushMessage()
  }

  @Throws(IOException::class)
  fun leave(callback: MessageCallback) {
    pushMessage(PhoenixEvent.LEAVE.phxEvent, callback = object : MessageCallback {
      override fun onMessage(status: String, phoenixResponse: PhoenixResponse?) {
        if (phoenixResponse?.event == PhoenixEvent.CLOSE.phxEvent) {
          callback.onMessage(status, phoenixResponse)
        }
      }

      override fun onFailure(throwable: Throwable?, phoenixResponse: PhoenixResponse?) {
        callback.onFailure(throwable, phoenixResponse)
      }
    })
  }

  /**
   * Unsubscribe for event notifications
   *
   * @param event The event name
   * @return The instance's self
   */
  fun off(event: String): Channel {
    synchronized(bindings) {
      val bindingIter = bindings.iterator()
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
   * @param event    The event name
   * @param callback The callback to be invoked with the event's message
   * @return The instance's self
   */
  fun on(event: String, callback: MessageCallback): Channel {
    synchronized(bindings) {
      this.bindings.add(Binding(event, callback))
    }
    return this
  }

  fun on(event: PhoenixEvent, callback: MessageCallback): Channel = on(event.phxEvent, callback)

  /**
   * Pushes a payload to be sent to the channel
   *
   * @param event   The event name
   * @param payload The message payload
   * @param timeout The number of milliseconds to wait before triggering a timeout
   * @throws IOException           Thrown if the payload cannot be pushed
   * @throws IllegalStateException Thrown if the channel has not yet been joined
   */
  @Throws(IOException::class)
  fun push(event: String, payload: JsonNode? = null, timeout: Long? = null, callback: MessageCallback? = null) {
    if (state != ChannelState.JOINED) {
      throw IllegalStateException("Unable to push event before channel has been joined")
    }
    pushMessage(event, payload, timeout, callback)
  }

  override fun toString(): String {
    return "Channel{" +
        "topic='" + topic + '\'' +
        ", bindings(" + bindings.size + ")=" + bindings +
        '}'
  }

  companion object {

    val DEFAULT_TIMEOUT: Long = 5000
  }
}
