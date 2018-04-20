package org.phoenixframework.socket

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
import org.phoenixframework.PhoenixRequest
import org.phoenixframework.PhoenixRequestSender
import org.phoenixframework.PhoenixResponse
import org.phoenixframework.channel.Channel
import org.phoenixframework.MessageCallback
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.concurrent.timerTask

class Socket @JvmOverloads constructor(
    private val endpointUri: String,
    private val heartbeatInterval: Long = DEFAULT_HEARTBEAT_INTERVAL,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule())
  : PhoenixRequestSender {

  private var webSocket: WebSocket? = null
  private val channels: ConcurrentHashMap<String, Channel> = ConcurrentHashMap()
  private var refNumber = 1

  private var listeners = mutableSetOf<PhoenixSocketEventListener>()

  private var timer: Timer = Timer("org.phoenixframework.socket.Socket Timer For $endpointUri")
  private var timeoutTimer: Timer = Timer("Timeout Timer For $endpointUri")
  private var heartbeatTimerTask: TimerTask? = null
  var reconnectOnFailure: Boolean = false
  private var reconnectTimerTask: TimerTask? = null

  private val timeoutTimerTasks = ConcurrentHashMap<String, TimerTask>()

  // buffer가 비어있으면 작업을 중지하고 blocking 상태가 됨.
  private var messageBuffer: LinkedBlockingQueue<String> = LinkedBlockingQueue()

  companion object {
    private const val DEFAULT_HEARTBEAT_INTERVAL: Long = 7000
    private const val DEFAULT_RECONNECT_INTERVAL: Long = 5000
    private const val DEFAULT_TIMEOUT: Long = 5000
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

  fun registerPhoenixSocketListener(phoenixSocketEventListener: PhoenixSocketEventListener) {
    listeners.add(phoenixSocketEventListener)
  }

  fun unregisterPhoenixSocketListener(phoenixSocketEventListener: PhoenixSocketEventListener) {
    listeners.remove(phoenixSocketEventListener)
  }

  private fun push(request: PhoenixRequest): Socket {
    send(objectMapper.writeValueAsString(request))
    return this@Socket
  }

  fun channel(topic: String): Channel {
    var channel = channels[topic]
    if (channel == null) {
      channel = Channel(this, topic, objectMapper)
      channels[topic] = channel
    }
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

  private fun flushSendBuffer() {
    messageBuffer.clear()
  }

  private fun isConnected(): Boolean = webSocket != null

  private fun startHeartbeatTimer() {
    cancelHeartbeatTimer()
    heartbeatTimerTask = timerTask {
      if (isConnected()) {
        try {
          push(PhoenixRequest("phoenix", "heartbeat",
              ObjectNode(JsonNodeFactory.instance), makeRef()))
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
    timer.schedule(heartbeatTimerTask, heartbeatInterval)
  }

  private fun cancelHeartbeatTimer() {
    heartbeatTimerTask?.cancel()
    heartbeatTimerTask = null
  }

  private fun startTimeoutTimer(channel: Channel, request: PhoenixRequest, timeout: Long) {
    val ref = request.ref!!
    val timeoutTimerTask = timerTask {
      channel.retrieveFailure(TimeoutException("Timeout from request " + request))
    }
    timeoutTimerTasks[ref] = timeoutTimerTask
    timeoutTimer.schedule(timeoutTimerTask, timeout)
  }

  private fun cancelTimeoutTimer(ref: String) {
    timeoutTimerTasks[ref]?.cancel()
    timeoutTimerTasks.remove(ref)
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
    timer.schedule(reconnectTimerTask, DEFAULT_RECONNECT_INTERVAL)
  }

  private fun cancelReconnectTimer() {
    reconnectTimerTask?.cancel()
    reconnectTimerTask = null
  }

  private fun triggerChannelError(throwable: Throwable?) {
    channels.values.forEach { it.retrieveFailure(throwable) }
  }

  /**
   * Implements [PhoenixRequestSender].
   */
  override fun canPushMessage(): Boolean = isConnected()

  override fun pushMessage(request: PhoenixRequest, timeout: Long?, callback: MessageCallback?) {
    startTimeoutTimer(channel(request.topic), request, timeout ?: DEFAULT_TIMEOUT)
    push(request)
  }

  override fun makeRef(): String {
    synchronized(refNumber) {
      val ref = refNumber++
      if (refNumber == Int.MAX_VALUE) {
        refNumber = 0
      }
      return ref.toString()
    }
  }

  private val phoenixWebSocketListener = object: WebSocketListener() {

    override fun onOpen(webSocket: WebSocket?, response: Response?) {
      this@Socket.webSocket = webSocket
      cancelReconnectTimer()
      startHeartbeatTimer()
      this@Socket.listeners.forEach { it.onOpen(response) }

      // 다시 join이 필요하다면 flush가 아닌 buffer clear가 필요.
      flushSendBuffer()
    }

    override fun onMessage(webSocket: WebSocket?, text: String?) {
      val message = this@Socket.objectMapper.readValue(text, PhoenixResponse::class.java)
      this@Socket.listeners.forEach { it.onMessage(text) }
      message.ref?.let { cancelTimeoutTimer(it) }
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
        this@Socket.listeners.forEach { it.onClosed(code, reason) }
      }
    }

    override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
      triggerChannelError(t)
      this@Socket.listeners.forEach { it.onFailure(t, response) }
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
}