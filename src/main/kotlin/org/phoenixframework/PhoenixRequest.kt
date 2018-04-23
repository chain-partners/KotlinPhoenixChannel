package org.phoenixframework

import com.fasterxml.jackson.databind.JsonNode
import org.phoenixframework.channel.StatusBinding

data class PhoenixRequest(
    val topic: String,
    val event: String? = null,
    val payload: JsonNode? = null,
    var ref: String? = null) {

  private val statusBindings = ArrayList<StatusBinding>()

  fun receive(status: String, callback: (PhoenixResponse) -> Unit): PhoenixRequest {
    synchronized(statusBindings) {
      statusBindings.add(StatusBinding(status, callback))
    }
    return this
  }

  internal fun matchReceive(status: String?, response: PhoenixResponse) {
    if (status != null) {
      statusBindings.filter { it.status == status }.forEach { it.callback.invoke(response) }
    }
  }
}