import com.fasterxml.jackson.databind.JsonNode

data class Push(val topic: String, val event: String, val payload: JsonNode? = null, val timeout: Long = Channel.DEFAULT_TIMEOUT)