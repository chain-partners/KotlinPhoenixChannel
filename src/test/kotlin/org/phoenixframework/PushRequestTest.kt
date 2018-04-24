package org.phoenixframework

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test

class PushRequestTest: TestBase() {

  private lateinit var request: PhoenixRequest

  private val topic = "topic"
  private val event = "event"
  private val payload = "{\"payload\":\"payload\"}"
  private val ref = "ref"

  private val objectMapper = ObjectMapper().registerKotlinModule()

  override fun setup() {
    super.setup()
    request = PhoenixRequest(topic, event, objectMapper.readTree(payload), ref = ref)
  }

  @Test
  fun matchReceiveTest() {
    val callback = spyk({ response: PhoenixResponse -> System.out.println(response) })
    val callback2 = spyk({ response: PhoenixResponse -> System.out.println("$response 2") })
    request.receive("ok", callback)
        .receive("ok", callback2)

    val phoenixResponse = PhoenixResponse(topic, event, objectMapper.readTree(payload), ref)
    request.matchReceive("ok", phoenixResponse)

    verify {
      callback.invoke(phoenixResponse)
      callback2.invoke(phoenixResponse)
    }
  }
}