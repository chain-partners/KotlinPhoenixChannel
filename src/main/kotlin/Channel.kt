import com.fasterxml.jackson.databind.JsonNode
import java.io.IOException
import java.util.ArrayList

class Channel(private val pushDelegate: PushDelegate, val topic: String, private val payload: JsonNode) {
  private val bindings = ArrayList<Binding>()

  private var state = ChannelState.CLOSED

  fun triggerChannelException(throwable: Throwable) {

  }

  /**
   * Initiates a channel join event
   *
   * @return This Push instance
   * @throws IllegalStateException Thrown if the channel has already been joined
   * @throws IOException           Thrown if the join could not be sent
   */
  @Throws(IllegalStateException::class, IOException::class)
  fun join(): Push {
    if (state == ChannelState.JOINED || state == ChannelState.JOINING) {
      throw IllegalStateException(
          "Tried to join multiple times. 'join' can only be invoked once per channel")
    }
    this.state = ChannelState.JOINING
    pushDelegate.pushMessage(this, Push(PhoenixEvent.JOIN.phxEvent, payload, DEFAULT_TIMEOUT))
    return this.joinPush
  }

  /**
   * Triggers event signalling to all callbacks bound to the specified event.
   * Do not call this method except for testing and [Socket].
   *
   * @param triggerEvent The event name
   * @param envelope     The message's envelope relating to the event or null if not relevant.
   */
  fun retrieveMessage(event: String, message: Message?, throwable: Throwable?) {
    when (event) {
      PhoenixEvent.JOIN.phxEvent -> {
        state = ChannelState.JOINED
      }
      PhoenixEvent.CLOSE.phxEvent -> {
        state = ChannelState.CLOSED
      }
      PhoenixEvent.ERROR.phxEvent -> {
        state = ChannelState.ERRORED
        // TODO(changhee): Rejoin channel with timer.
        bindings.filter { it.event == event }
            .forEach { it.callback?.onFailure(throwable, message) }
      }
      PhoenixEvent.REPLY.phxEvent -> {
        bindings.filter { it.event == event }
            .forEach { it.callback?.onMessage("ok", message) }
      }
    }
  }

  /**
   * @return true if the socket is open and the channel has joined
   */
  private fun canPush(): Boolean {
    return this.state === ChannelState.JOINED && this.pushDelegate.canPushMessage()
  }

  fun isMember(topic: String): Boolean {
    return this.topic == topic
  }



  @Throws(IOException::class)
  fun leave(): Push {
    return this.push(PhoenixEvent.LEAVE.getPhxEvent()).receive("ok", object : IMessageCallback() {
      fun onMessage(envelope: Envelope) {
        this@Channel.trigger(PhoenixEvent.CLOSE.getPhxEvent(), null)
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
        if (bindingIter.next().getEvent().equals(event)) {
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
  fun on(event: String, callback: IMessageCallback): Channel {
    synchronized(bindings) {
      this.bindings.add(Binding(event, callback))
    }
    return this
  }

  private fun onClose(callback: IMessageCallback) {
    this.on(PhoenixEvent.CLOSE.getPhxEvent(), callback)
  }

  /**
   * Register an error callback for the channel
   *
   * @param callback Callback to be invoked on error
   */
  private fun onError(callback: IErrorCallback) {
    this.on(PhoenixEvent.ERROR.getPhxEvent(), object : IMessageCallback() {
      fun onMessage(envelope: Envelope?) {
        var reason: String? = null
        if (envelope != null) {
          reason = envelope!!.getReason()
        }
        callback.onError(IOException(reason))
      }
    })
  }

  /**
   * Pushes a payload to be sent to the channel
   *
   * @param event   The event name
   * @param payload The message payload
   * @param timeout The number of milliseconds to wait before triggering a timeout
   * @return The Push instance used to send the message
   * @throws IOException           Thrown if the payload cannot be pushed
   * @throws IllegalStateException Thrown if the channel has not yet been joined
   */
  @Throws(IOException::class, IllegalStateException::class)
  private fun push(event: String, payload: JsonNode?, timeout: Long): Push {
    if (state != ChannelState.JOINED) {
      throw IllegalStateException("Unable to push event before channel has been joined")
    }
    val pushEvent = Push(event, payload, timeout)
    if (this.canPush()) {
      pushDelegate.pushMessage(this, pushEvent)
    } else {
      this.pushBuffer.add(pushEvent)
    }
    return pushEvent
  }

  @Throws(IOException::class)
  @JvmOverloads
  fun push(event: String, payload: JsonNode? = null, success: ((Message) -> Unit)? = null, failure: ((t: Throwable?) -> Unit)? = null): Push {
    return push(event, payload, DEFAULT_TIMEOUT)
  }

  override fun toString(): String {
    return "Channel{" +
        "topic='" + topic + '\'' +
        ", message=" + payload +
        ", bindings(" + bindings.size + ")=" + bindings +
        '}'
  }

  @Throws(IOException::class)
  private fun sendJoin(payload: JsonNode?) {

  }

  companion object {

    private val DEFAULT_TIMEOUT: Long = 5000

    private val log = LoggerFactory.getLogger(Channel::class.java)
  }
}
