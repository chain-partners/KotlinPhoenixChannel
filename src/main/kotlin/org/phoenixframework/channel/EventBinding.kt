package org.phoenixframework.channel

import org.phoenixframework.MessageCallback

data class EventBinding(val event: String, val callback: MessageCallback?)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.EventBinding{" +
        "event='" + event + '\'' +
        ", callback=" + callback +
        '}'
  }
}