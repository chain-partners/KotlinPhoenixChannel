package org.phoenixframework

import org.phoenixframework.PhoenixResponse

interface MessageCallback {

  fun onMessage(status: String, phoenixResponse: PhoenixResponse?)

  fun onFailure(throwable: Throwable?, phoenixResponse: PhoenixResponse?)
}