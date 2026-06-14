package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ChannelControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun channels() {
        doReturn(listOf(createBootstrap("007", channels = listOf("telegram", "email")))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/channels", List::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(2, body.size)
        assertEquals("telegram", (body[0] as Map<*, *>)["name"])
        assertEquals("email", (body[1] as Map<*, *>)["name"])
    }

    @Test
    fun `channels returns empty list when no channels`() {
        doReturn(listOf(createBootstrap("007", channels = emptyList()))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/channels", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body!!.size)
    }

    @Test
    fun `channels returns 404 when agent not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/channels", List::class.java)

        assertEquals(404, response.statusCode.value())
    }

    private fun createBootstrap(name: String, channels: List<String> = emptyList()): Bootstrap {
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val channelRegistry = mock<ChannelRegistry>()
        doReturn(channels.map { channelName ->
            mock<Channel>().also { doReturn(channelName).whenever(it).name() }
        }).whenever(channelRegistry).all()

        val context = Context(
            assistant = assistant,
            home = File("target/channel-controller/$name"),
            llm = mock<LLM>(),
            channelRegistry = channelRegistry,
        )
        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
