package org.phoenixframework.socket

import okhttp3.Response

/**
 * Each callbacks corresponds to that of [okhttp3.WebSocketListener].
 */
interface PhoenixSocketListener {

  fun onOpen(response: Response?)

  fun onClosing(code: Int?, reason: String?)

  fun onClosed(code: Int?, reason: String?)

  fun onFailure(t: Throwable?, response: Response?)

  fun onMessage(text: String?)
}