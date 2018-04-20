package org.phoenixframework

interface PhoenixResponseCallback {

  fun onResponse(response: PhoenixResponse? = null)

  fun onFailure(throwable: Throwable? = null, response: PhoenixResponse? = null)
}