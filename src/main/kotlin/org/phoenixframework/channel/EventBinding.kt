package org.phoenixframework.channel

import org.phoenixframework.Message

data class EventBinding(val event: String,
    val success: ((Message?) -> Unit)? = null,
    val failure: ((Throwable?, Message?) -> Unit)? = null)  {

  override fun toString(): String {
    return "org.phoenixframework.channel.EventBinding" +
        "{event='$event', success='$success', failure='$failure'}"
  }
}