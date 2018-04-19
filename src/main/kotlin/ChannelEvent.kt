enum class ChannelEvent (val phxEvent: String) {
  CLOSE("phx_close"),
  ERROR("phx_error"),
  JOIN("phx_join"),
  REPLY("phx_reply"),
  LEAVE("phx_leave");

  companion object {

    fun getEvent(phxEvent: String): ChannelEvent? = values().find { it.phxEvent == phxEvent }
  }
}
