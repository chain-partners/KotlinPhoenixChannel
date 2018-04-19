data class Binding(val event: String, val callback: MessageCallback?)  {

  override fun toString(): String {
    return "Binding{" +
        "event='" + event + '\'' +
        ", callback=" + callback +
        '}'
  }
}