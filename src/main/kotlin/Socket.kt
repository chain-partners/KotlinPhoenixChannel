import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.timerTask

class Socket @JvmOverloads constructor(
    private val endpointUri: String,
    private val heartbeatInterval: Int = DEFAULT_HEARTBEAT_INTERVAL,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()): PushDelegate {

  private var webSocket: WebSocket? = null
  private val channels: ConcurrentHashMap<String, Channel> = ConcurrentHashMap()
  private var refNumber = 1

  private var listeners = mutableSetOf<PhoenixSocketListener>()

  private var timer: Timer = Timer("Reconnect Timer For $endpointUri")
  private var heartbeatTimerTask: TimerTask? = null
  var reconnectOnFailure: Boolean = false
  private var reconnectTimerTask: TimerTask? = null

  // buffer가 비어있으면 작업을 중지하고 blocking 상태가 됨.
  private var messageBuffer: LinkedBlockingQueue<String> = LinkedBlockingQueue()

  companion object {
    private const val DEFAULT_HEARTBEAT_INTERVAL = 7000
    private const val DEFAULT_RECONNECT_INTERVAL = 5000
  }

  fun connect() {
    disconnect()
    val httpUrl = endpointUri.replaceFirst("ws:", "http:")
        .replaceFirst("wss:", "https:")
    val request = Request.Builder().url(httpUrl).build()
    webSocket = httpClient.newWebSocket(request, phoenixWebSocketListener)
  }

  fun disconnect() {
    webSocket?.close(1001, "Disconnect By Client")
    cancelReconnectTimer()
    cancelHeartbeatTimer()
  }

  fun registerPhoenixSocketListener(phoenixSocketListener: PhoenixSocketListener) {
    listeners.add(phoenixSocketListener)
  }

  fun unregisterPhoenixSocketListener(phoenixSocketListener: PhoenixSocketListener) {
    listeners.remove(phoenixSocketListener)
  }

  fun push(message: Message): Socket {
    val node = objectMapper.createObjectNode()
    node.put("topic", message.topic)
        .put("event", message.event)
        .put("ref", message.ref)
        .set("payload", message.payload ?: objectMapper.createObjectNode())
    send(objectMapper.writeValueAsString(node))
    return this@Socket
  }

  fun channel(topic: String, payload: JsonNode): Channel {
    val channel = Channel(this, topic, payload)
    channels[topic] = channel
    return channel
  }

  fun removeChannel(topic: String) {
    channels.remove(topic)
  }

  fun removeAllChannels() {
    channels.clear()
  }

  private fun send(json: String) {
    messageBuffer.put(json)
    while (isConnected() && messageBuffer.isNotEmpty()) {
      webSocket?.send(messageBuffer.take())
    }
  }

  private fun makeRef(): String {
    synchronized(refNumber) {
      val ref = refNumber++
      if (refNumber == Int.MAX_VALUE) {
        refNumber = 0
      }
      return ref.toString()
    }
  }

  private fun isConnected(): Boolean = webSocket != null

  private fun startHeartbeatTimer() {
    cancelHeartbeatTimer()
    heartbeatTimerTask = timerTask {
      if (isConnected()) {
        try {
          push(Message("phoenix", "heartbeat",
              ObjectNode(JsonNodeFactory.instance), makeRef()))
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
    timer.schedule(heartbeatTimerTask, heartbeatInterval.toLong())
  }

  private fun cancelHeartbeatTimer() {
    heartbeatTimerTask?.cancel()
    heartbeatTimerTask = null
  }

  private fun startReconnectTimer() {
    cancelReconnectTimer()
    cancelHeartbeatTimer()
    reconnectTimerTask = timerTask {
      try {
        connect()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    timer.schedule(reconnectTimerTask, DEFAULT_RECONNECT_INTERVAL.toLong())
  }

  private fun cancelReconnectTimer() {
    reconnectTimerTask?.cancel()
    reconnectTimerTask = null
  }

  private fun triggerChannelError() {
//      channels.forEach()
  }

  private val phoenixWebSocketListener = object: WebSocketListener() {

    override fun onOpen(webSocket: WebSocket?, response: Response?) {
      this@Socket.webSocket = webSocket
      cancelReconnectTimer()
      startHeartbeatTimer()
      this@Socket.listeners.forEach { it.onOpen(response) }
    }

    override fun onMessage(webSocket: WebSocket?, text: String?) {
      val message = this@Socket.objectMapper.readValue(text, Message::class.java)
      this@Socket.listeners.forEach { it.onMessage(text) }
      this@Socket.channels[message.topic]?.retrieveMessage(message)
    }

    override fun onMessage(webSocket: WebSocket?, bytes: ByteString?) {
      onMessage(webSocket, bytes.toString())
    }

    override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
      this@Socket.listeners.forEach { it.onClosing(code, reason) }
    }

    override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
      this@Socket.apply {
        this@Socket.webSocket = null
        this@Socket.listeners.forEach{ it.onClosed(code, reason) }
      }
    }

    override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
      triggerChannelError()
      t?.let {
        this@Socket.listeners.forEach { it.onFailure(t, response) }
      }
      try {
        this@Socket.webSocket?.close(1001 /* GOING_AWAY */, "Error Occurred")
      } finally {
        this@Socket.webSocket = null
        if (this@Socket.reconnectOnFailure) {
          startReconnectTimer()
        }
      }
    }
  }

  override fun pushMessage(channel: Channel, push: Push) {

  }
}