package org.phoenixframework.channel

import org.phoenixframework.PhoenixResponseCallback

data class EventBinding(val event: String, val callback: PhoenixResponseCallback?)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.EventBinding{" +
        "event='" + event + '\'' +
        ", callback=" + callback +
        '}'
  }
}