package org.phoenixframework

interface PhoenixRequestSender {

  fun makeRef(): String
  fun pushRequest(request: PhoenixRequest, timeout: Long?)
  fun canPushRequest(): Boolean
}