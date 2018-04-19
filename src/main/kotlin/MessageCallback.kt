interface MessageCallback {

  fun onMessage(status: String, message: Message?)

  fun onFailure(throwable: Throwable?, message: Message?)
}