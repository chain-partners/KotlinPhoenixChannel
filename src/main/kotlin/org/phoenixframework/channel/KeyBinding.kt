package org.phoenixframework.channel

import org.phoenixframework.Message

data class KeyBinding(val key: String,
    val success: ((Message?) -> Unit)? = null,
    val failure: ((Message?, Throwable?) -> Unit)? = null)  {

  override fun toString(): String {
    return "KeyBinding" +
        "{key='$key', success='$success', failure='$failure'}"
  }
}