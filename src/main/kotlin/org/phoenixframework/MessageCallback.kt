package org.phoenixframework

interface MessageCallback {

  fun onMessage(status: String, response: PhoenixResponse? = null)

  fun onFailure(throwable: Throwable? = null, response: PhoenixResponse? = null)
}