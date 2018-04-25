package org.phoenixframework

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val topic: String,
    val event: String? = null,
    val payload: String? = null,
    var ref: String? = null) {

  var responseStatus: String? = null
  var reason: String? = null
}