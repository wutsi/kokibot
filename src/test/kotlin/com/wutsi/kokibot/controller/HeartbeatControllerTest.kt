package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.ConfigurationException
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class HeartbeatControllerTest {
    companion object {
        const val MAX_CONTEXT_WINDOW = 10240
    }

    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    // GET /heartbeat

    @Test
    fun `get heartbeat`() {
        doReturn(
            listOf(createBootstrap("007", heartbeatEnabled = true, heartbeatFrequency = 30L, heartbeatInstructions = "Tick every hour"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/heartbeat", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["enabled"])
        assertEquals(30, response.body!!["frequency"])
        assertEquals("Tick every hour", response.body!!["instructions"])
    }

    @Test
    fun `get heartbeat - no instructions`() {
        doReturn(
            listOf(createBootstrap("007", heartbeatEnabled = false, heartbeatFrequency = 60L, heartbeatInstructions = null))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/heartbeat", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(false, response.body!!["enabled"])
        assertEquals(60, response.body!!["frequency"])
        assertEquals("", response.body!!["instructions"])
    }

    @Test
    fun `get heartbeat not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/heartbeat", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    // POST /heartbeat/settings

    @Test
    fun `post heartbeat settings`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/heartbeat/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("heartbeat.enabled", false)
    }

    @Test
    fun `post heartbeat settings - invalid value`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(ConfigurationException("Invalid setting")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/heartbeat/settings",
            mapOf("key" to "enabled", "value" to "bad"),
            Map::class.java
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `post heartbeat settings - missing key`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/heartbeat/settings",
            mapOf("value" to false),
            Map::class.java
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `post heartbeat settings - missing value`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/heartbeat/settings",
            mapOf("key" to "enabled"),
            Map::class.java
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `post heartbeat settings not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.exchange(
            "/assistants/xxx/heartbeat/settings",
            HttpMethod.POST,
            HttpEntity(mapOf("key" to "enabled", "value" to false)),
            Map::class.java
        )

        assertEquals(404, response.statusCode.value())
    }

    private fun createBootstrap(
        name: String,
        description: String? = null,
        instructions: String? = null,
        heartbeatEnabled: Boolean = true,
        heartbeatFrequency: Long = 30L,
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
        doReturn(heartbeatEnabled).whenever(heartbeat).isEnabled()
        doReturn(heartbeatFrequency).whenever(heartbeat).getFrequency()
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
