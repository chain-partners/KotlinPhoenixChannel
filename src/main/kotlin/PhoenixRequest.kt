import com.fasterxml.jackson.databind.JsonNode

data class PhoenixRequest(
    val topic: String,
    val event: String? = null,
    val payload: JsonNode? = null,
    var ref: String? = null)