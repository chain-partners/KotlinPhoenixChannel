package org.phoenixframework.channel

import org.phoenixframework.PhoenixResponse

data class StatusBinding(val status: String, val callback: (PhoenixResponse) -> Unit)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.StatusBinding{" +
        "status='" + status + '\'' +
        ", callback=" + callback +
        '}'
  }
}