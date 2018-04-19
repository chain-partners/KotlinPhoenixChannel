import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class PhoenixResponse(
    val topic: String,
    val event: String,
    val payload: JsonNode?,
    val ref: String?) {

  val repsonseStatus: String? = payload?.get("status")?.textValue()
  val reason = payload?.get("reason")?.textValue()
}