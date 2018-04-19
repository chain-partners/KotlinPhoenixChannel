interface MessageCallback {

  fun onMessage(status: String, phoenixResponse: PhoenixResponse?)

  fun onFailure(throwable: Throwable?, phoenixResponse: PhoenixResponse?)
}