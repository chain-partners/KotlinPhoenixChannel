package org.phoenixframework.channels

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

// To fix UnrecognizedPropertyException.
@JsonIgnoreProperties(ignoreUnknown = true)
class Envelope {
    @JsonProperty(value = "topic")
    val topic: String

    @JsonProperty(value = "event")
    val event: String

    @JsonProperty(value = "payload")
    val payload: JsonNode

    @JsonProperty(value = "ref")
    private val ref: String?

    /**
     * Helper to retrieve the value of "status" from the payload
     *
     * @return The status string or null if not found
     */
    val responseStatus: String?
        get() {
            val statusNode = payload.get("status")
            return statusNode?.textValue()
        }

    /**
     * Helper to retrieve the value of "reason" from the payload
     *
     * @return The reason string or null if not found
     */
    val reason: String?
        get() {
            val reasonNode = payload.get("reason")
            return reasonNode?.textValue()
        }

    constructor() {}

    constructor(topic: String, event: String, payload: JsonNode, ref: String) {
        this.topic = topic
        this.event = event
        this.payload = payload
        this.ref = ref
    }

    /**
     * Helper to retrieve the value of "ref" from the payload
     *
     * @return The ref string or null if not found
     */
    fun getRef(): String? {
        if (ref != null) return ref
        val refNode = payload.get("ref")
        return refNode?.textValue()
    }

    override fun toString(): String {
        return "Envelope{" +
                "topic='" + topic + '\'' +
                ", event='" + event + '\'' +
                ", payload=" + payload +
                '}'
    }
}
