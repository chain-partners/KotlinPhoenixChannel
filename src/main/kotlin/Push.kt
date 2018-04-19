import com.fasterxml.jackson.databind.JsonNode

data class Push(val topic: String, val event: String, val payload: JsonNode?, val timeout: Long)