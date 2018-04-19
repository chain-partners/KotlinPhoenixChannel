import com.fasterxml.jackson.databind.JsonNode

interface PushDelegate {

  fun pushMessage(channel: Channel, push: Push)
}