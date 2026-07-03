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
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.LinkedMultiValueMap
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AssistantControllerTest {
    companion object {
        const val MAX_CONTEXT_WINDOW = 10240
    }

    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun list() {
        doReturn(
            listOf(createBootstrap("007"), createBootstrap("008"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants", List::class.java)

        assertEquals(2, response.body?.size)
        assertEquals(listOf("007", "008"), response.body?.map { item ->
            (item as Map<*, *>).get("name")
        })
    }

    @Test
    fun `list - limit`() {
        doReturn(
            listOf(createBootstrap("007"), createBootstrap("008"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants?limit=1", List::class.java)

        assertEquals(1, response.body?.size)
        assertEquals(listOf("007"), response.body?.map { item ->
            (item as Map<*, *>).get("name")
        })
    }

    @Test
    fun `list - exclude`() {
        doReturn(
            listOf(createBootstrap("007"), createBootstrap("008"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants?exclude=008", List::class.java)

        assertEquals(1, response.body?.size)
        assertEquals(listOf("007"), response.body?.map { item ->
            (item as Map<*, *>).get("name")
        })
    }

    @Test
    fun get() {
        doReturn(
            listOf(createBootstrap("007", description = "Hello world"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Hello world", response.body!!["description"])
    }

    @Test
    fun `get not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `context-window returns baseline and max`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity(
            "/assistants/007/context-window?userId=user1&channelId=channel:telegram",
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(500, response.body!!["baseline"])
        assertEquals(MAX_CONTEXT_WINDOW, response.body!!["max"])
    }

    @Test
    fun `context-window returns 404 when assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity(
            "/assistants/unknown/context-window?userId=user1&channelId=channel:telegram",
            Any::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `icon returns PNG content`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val iconFile = File("target/assistant-controller/007/config/icon.png")
        iconFile.parentFile.mkdirs()
        iconFile.writeBytes(byteArrayOf(1, 2, 3))

        val response = rest.getForEntity("/assistants/007/icon.png", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("image/png", response.headers.contentType?.toString())
    }

    @Test
    fun `icon returns 404 when file missing`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        File("target/assistant-controller/007/config/icon.png").delete()

        val response = rest.getForEntity("/assistants/007/icon.png", Any::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `icon returns 404 when assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/icon.png", Any::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `upload icon stores file`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val iconBytes = byteArrayOf(1, 2, 3)
        val resource = object : ByteArrayResource(iconBytes) {
            override fun getFilename() = "icon.png"
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.IMAGE_PNG
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val response = rest.postForEntity(
            "/assistants/007/icon.png",
            HttpEntity(body, headers),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        assertTrue(File("target/assistant-controller/007/config/icon.png").exists())
    }

    @Test
    fun `upload icon returns 404 when assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val resource = object : ByteArrayResource(byteArrayOf(1, 2, 3)) {
            override fun getFilename() = "icon.png"
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.IMAGE_PNG
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val response = rest.postForEntity(
            "/assistants/xxx/icon.png",
            HttpEntity(body, headers),
            Any::class.java
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set - success`() {
        val bootstrap = mock(Bootstrap::class.java)
        val assistant = mock<Assistant>()
        doReturn("007").whenever(assistant).name
        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/007"),
            llm = mock(),
        )
        doReturn(context).whenever(bootstrap).getContext()
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/settings",
            mapOf("key" to "description", "value" to "hello"),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("description", "hello")
    }

    @Test
    fun `set - name`() {
        val bootstrap = mock(Bootstrap::class.java)
        val assistant = mock<Assistant>()
        doReturn("007").whenever(assistant).name
        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/007"),
            llm = mock(),
        )
        doReturn(context).whenever(bootstrap).getContext()
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/settings",
            mapOf("key" to "assistant.name", "value" to "hello"),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(multi).rename("007", "hello")
    }

    @Test
    fun `set - not found when assistant name unknown`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/xxx/settings",
            mapOf("key" to "description", "value" to "hello"),
            Map::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set - bad request when key missing from body`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/settings",
            mapOf("value" to "hello"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `set - bad request when value missing from body`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/settings",
            mapOf("key" to "description"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `set - bad request when unknown key`() {
        val bootstrap = mock(Bootstrap::class.java)
        val assistant = mock<Assistant>()
        doReturn("007").whenever(assistant).name
        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/007"),
            llm = mock<LLM>(),
        )
        doReturn(context).whenever(bootstrap).getContext()
        doThrow(ConfigurationException("Unknown assistant setting: invalid")).whenever(bootstrap).set(any(), any())
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/settings",
            mapOf("key" to "invalid", "value" to "hello"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("Unknown assistant setting: invalid", (response.body as Map<*, *>)["error"])
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
        doReturn("deepseek").whenever(llm).getName()
        doReturn("deepseek-v4.0").whenever(llm).getModel()
        doReturn(MAX_CONTEXT_WINDOW).whenever(llm).getMaxContextWindow()
        doReturn(balance).whenever(llm).balance()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name
        doReturn(description).whenever(assistant).getDescription()
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
