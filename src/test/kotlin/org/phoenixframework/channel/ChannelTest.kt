package org.phoenixframework.channel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Test
import org.phoenixframework.PhoenixRequest
import org.phoenixframework.PhoenixRequestSender
import org.phoenixframework.TestBase

class ChannelTest: TestBase() {

  @RelaxedMockK
  lateinit var requestSender: PhoenixRequestSender

  private lateinit var phxChannel: Channel

  private val topic = "topic"
  private val objectMapper = jacksonObjectMapper()

  override fun setup() {
    super.setup()
    phxChannel = Channel(requestSender, topic, objectMapper)
  }

  @Test
  fun joinTest() {
    every { requestSender.makeRef() } returns "ref_1"

    phxChannel.join()

    verify {
      requestSender.pushMessage(
          request = PhoenixRequest("topic", "phx_join", null, "ref_1"),
          timeout = null)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun `cannot join twice`() {
    every { requestSender.makeRef() } returns "ref_1"
    phxChannel.join()
    phxChannel.join()
  }
}

