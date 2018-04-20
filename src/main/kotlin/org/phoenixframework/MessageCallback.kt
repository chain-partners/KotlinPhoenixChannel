package org.phoenixframework

interface MessageCallback {

  fun onMessage(response: PhoenixResponse? = null)

  fun onFailure(throwable: Throwable? = null, response: PhoenixResponse? = null)
}