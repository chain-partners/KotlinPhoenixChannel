package org.phoenixframework

interface PhoenixMessageSender {

  fun makeRef(): String
  fun sendMessage(message: Message, timeout: Long?)
  fun canSendMessage(): Boolean
}