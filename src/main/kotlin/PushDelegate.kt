interface PushDelegate {

  fun pushMessage(channel: Channel, push: Push)
  fun canPushMessage(): Boolean
}