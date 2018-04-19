package org.phoenixframework

internal interface PhoenixRequestSender {

  fun makeRef(): String
  fun pushMessage(request: PhoenixRequest, timeout: Long?, callback: MessageCallback?)
  fun canPushMessage(): Boolean
}