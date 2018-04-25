package org.phoenixframework

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val topic: String,
    val event: String? = null,
    val payload: String? = null,
    var ref: String? = null) {

  var status: String? = null
  var reason: String? = null
}