package org.phoenixframework.socket

/**
 * Each events corresponds to callbacks of [okhttp3.WebSocketListener].
 */
enum class SocketEvent {
  OPEN,
  CLOSING,
  CLOSED,
  FAILURE,
  MESSAGE
}