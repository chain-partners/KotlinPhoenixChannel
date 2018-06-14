package org.phoenixframework.socket

/**
 * Each callbacks corresponds to that of [okhttp3.WebSocketListener].
 */
interface PhoenixSocketEventListener {

  fun onOpen(socket: Socket)

  fun onClosing(socket: Socket, code: Int?, reason: String?)

  fun onClosed(socket: Socket, code: Int?, reason: String?)

  fun onFailure(socket: Socket, t: Throwable?)

  fun onMessage(socket: Socket, text: String?)
}