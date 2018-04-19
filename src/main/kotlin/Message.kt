import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val topic: String,
    val event: String,
    val payload: JsonNode?,
    private val _ref: String?) {

  val ref: String? = _ref ?: payload?.get("ref")?.textValue()

  fun getResponseStatus(): String? = payload?.get("status")?.textValue()
  fun getReason(): String? = payload?.get("reason")?.textValue()
}