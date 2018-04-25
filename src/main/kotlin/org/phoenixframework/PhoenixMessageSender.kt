package org.phoenixframework

interface PhoenixMessageSender {

  fun makeRef(): String
  fun pushMessage(message: Message, timeout: Long?)
  fun canPushMessage(): Boolean
}