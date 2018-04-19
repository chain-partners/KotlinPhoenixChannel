internal interface PhoenixRequestSender {

  fun pushMessage(request: PhoenixRequest, timeout: Long?, callback: MessageCallback?): String
  fun canPushMessage(): Boolean
}