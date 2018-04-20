package org.phoenixframework.socket

import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SocketTest {

  private val mockServer = MockWebServer()

  private lateinit var phxSocket: Socket

  private lateinit var socketEventListener: PhoenixSocketEventListener

  @Before
  fun setup() {
    // localhost:4000
    mockServer.start(4000)
    phxSocket = Socket("ws://localhost:4000/socket/websocket")
    socketEventListener = object : PhoenixSocketEventListener {
      override fun onOpen(response: Response?) {
      }

      override fun onClosing(code: Int?, reason: String?) {
      }

      override fun onClosed(code: Int?, reason: String?) {
      }

      override fun onFailure(t: Throwable?, response: Response?) {
      }

      override fun onMessage(text: String?) {
      }
    }
    phxSocket.registerPhoenixSocketListener(socketEventListener)
  }

  @Test
  fun connectTest() {
    // TODO(changhee): Connect to MockWebServer.
    phxSocket.connect()
  }

  @After
  fun tearDown() {
    phxSocket.unregisterPhoenixSocketListener(socketEventListener)
  }
}
