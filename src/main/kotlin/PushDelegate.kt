import com.fasterxml.jackson.databind.JsonNode

interface PushDelegate {

  fun pushMessage(channel: Channel, event: String, payload: JsonNode?, timeout: Long?)
}