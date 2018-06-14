package org.phoenixframework.socket

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SocketTest {

  private val mockServer = MockWebServer()

  private lateinit var phxSocket: Socket

  private lateinit var socketEventListener: TestSocketEventListener

  @Before
  fun setup() {
    // localhost:4000
    mockServer.start(4000)
    phxSocket = Socket(endpointUri = "ws://localhost:4000/socket/websocket")
    socketEventListener = TestSocketEventListener()

    phxSocket.registerEventListener(socketEventListener)
  }

  @Test
  fun connectTest() = runBlocking {
    mockServer.enqueue(MockResponse().withWebSocketUpgrade(phxSocket.getWebSocketListener()))

    phxSocket.connect()
    delay(100, TimeUnit.MILLISECONDS)

    assertEquals("open", socketEventListener.socketState)
  }

  @Test
  fun channelTest() {
    val topic1 = "/api:topic1"
    val channel1 = phxSocket.channel(topic1)

    val actual = phxSocket.getChannels()
    assertEquals(1, actual.size)
    assertEquals("/api:topic1", actual[topic1]?.topic)

    // Does not create channel again about same topic.
    val channel2 = phxSocket.channel(topic1)

    assertEquals(1, actual.size)
    assertEquals(channel1, channel2)
  }

  @After
  fun tearDown() {
    phxSocket.unregisterEventListener(socketEventListener)
    mockServer.shutdown()
  }

  private class TestSocketEventListener: PhoenixSocketEventListener {
    var socketState: String = "closed"

    override fun onOpen(socket: Socket) {
      socketState = "open"
    }

    override fun onClosing(socket: Socket, code: Int?, reason: String?) {
      socketState = "closing"
    }

    override fun onClosed(socket: Socket, code: Int?, reason: String?) {
      socketState = "closed"
    }

    override fun onFailure(socket: Socket, t: Throwable?) {
      socketState = "failure"
    }

    override fun onMessage(socket: Socket, text: String?) {
    }
  }
}
