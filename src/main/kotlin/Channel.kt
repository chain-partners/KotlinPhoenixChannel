import com.fasterxml.jackson.databind.JsonNode

class Channel(val topic: String, val payload: JsonNode, val socket: Socket) {

  fun triggerChannelException(throwable: Throwable) {

  }
}