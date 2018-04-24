package org.phoenixframework.channel

import org.phoenixframework.PhoenixResponse

data class EventBinding(val event: String,
    val success: ((PhoenixResponse?) -> Unit)? = null,
    val failure: ((Throwable?, PhoenixResponse?) -> Unit)? = null)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.EventBinding" +
        "{event='$event', success='$success', failure='$failure'}"
  }
}