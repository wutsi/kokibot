package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.assistant.ContextWindow
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMBalance
import com.wutsi.kokibot.service.heartbeat.Heartbeat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.AssertionErrors.assertNull
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LLMControllerTest {
    companion object {
        const val MAX_CONTEXT_WINDOW = 10240
    }

    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun llm() {
        val balance = LLMBalance(currency = "USD", total = 100.0)
        doReturn(
            listOf(createBootstrap("007", balance = balance))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/llm", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("deepseek", response.body!!["name"])
        assertEquals("deepseek-v4.0", response.body!!["model"])
        assertEquals(MAX_CONTEXT_WINDOW, response.body!!["maxContextWindow"])

        val availableBalance = response.body!!["availableBalance"] as Map<String, *>
        assertEquals(100.0, availableBalance["amount"])
        assertEquals("USD", availableBalance["currency"])
        assertEquals(true, availableBalance["text"].toString().contains("\$100.00"))
    }

    @Test
    fun `llm no balance`() {
        doReturn(
            listOf(createBootstrap("007", balance = null))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/llm", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("deepseek", response.body!!["name"])
        assertEquals("deepseek-v4.0", response.body!!["model"])
        assertEquals(MAX_CONTEXT_WINDOW, response.body!!["maxContextWindow"])
        assertNull("availableBalance", response.body!!["availableBalance"])
    }

    private fun createBootstrap(
        name: String,
        description: String? = null,
        instructions: String? = null,
        heartbeatInstructions: String? = null,
        balance: LLMBalance? = null,
        channelIds: List<String> = emptyList(),
    ): Bootstrap {
        val llm = mock<LLM>()
        doReturn("deepseek").whenever(llm).name()
        doReturn("deepseek-v4.0").whenever(llm).model()
        doReturn(MAX_CONTEXT_WINDOW).whenever(llm).maxContextWindow()
        doReturn(balance).whenever(llm).balance()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name
        doReturn(description).whenever(assistant).description
        doReturn(instructions).whenever(assistant).getInstructions()
        doReturn(ContextWindow(baseline = 500, max = MAX_CONTEXT_WINDOW)).whenever(assistant)
            .contextWindow(any(), any(), anyOrNull())

        val heartbeat = mock<Heartbeat>()
        doReturn(heartbeatInstructions).whenever(heartbeat).getInstructions()

        val channelRegistry = mock<ChannelRegistry>()
        if (channelIds.isEmpty()) {
            doThrow(ChannelNotFoundException::class).whenever(channelRegistry).get(any())
        } else {
            channelIds.forEach { id ->
                val channel = mock<Channel>()
                doReturn(id).whenever(channel).id()
                doReturn(channel).whenever(channelRegistry).get(id)
            }
        }

        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/$name"),
            llm = llm,
            heartbeat = heartbeat,
            channelRegistry = channelRegistry,
        )
        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
