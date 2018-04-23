package org.phoenixframework.socket

/**
 * Each callbacks corresponds to that of [okhttp3.WebSocketListener].
 */
interface PhoenixSocketEventListener {

  fun onOpen()

  fun onClosing(code: Int?, reason: String?)

  fun onClosed(code: Int?, reason: String?)

  fun onFailure(t: Throwable?)

  fun onMessage(text: String?)
}