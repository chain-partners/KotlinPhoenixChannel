package org.phoenixframework

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val topic: String,
    val event: String? = null,
    val payload: JsonNode? = null,
    var ref: String? = null) {

  val responseStatus: String? = payload?.get("status")?.textValue()
  val reason = payload?.get("reason")?.textValue()
}