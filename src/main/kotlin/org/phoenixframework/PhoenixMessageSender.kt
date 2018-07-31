package org.phoenixframework

interface PhoenixMessageSender {

  /**
   * Create [Message] with [topic], [event] and [payload].
   *
   * @return [Message.ref] from sent [Message].
   */
  fun sendMessage(topic: String, event: String?, payload: String?, timeout: Long?): String
  fun canSendMessage(): Boolean
}
