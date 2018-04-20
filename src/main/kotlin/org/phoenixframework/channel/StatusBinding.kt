package org.phoenixframework.channel

import org.phoenixframework.PhoenixResponseCallback

data class StatusBinding(val status: String, val callback: PhoenixResponseCallback)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.StatusBinding{" +
        "status='" + status + '\'' +
        ", callback=" + callback +
        '}'
  }
}