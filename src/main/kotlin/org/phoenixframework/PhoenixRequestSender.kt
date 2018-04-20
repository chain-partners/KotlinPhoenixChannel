package org.phoenixframework

internal interface PhoenixRequestSender {

  fun makeRef(): String
  fun pushMessage(request: PhoenixRequest, timeout: Long?)
  fun canPushMessage(): Boolean
}