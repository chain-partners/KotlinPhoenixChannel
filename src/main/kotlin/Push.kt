import com.fasterxml.jackson.databind.JsonNode

class Push(val event: String, val payload: JsonNode?, val timeout: Long) {

}