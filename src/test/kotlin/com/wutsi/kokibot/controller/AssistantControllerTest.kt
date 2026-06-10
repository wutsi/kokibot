package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
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

        assertEquals(2, response.body!!.size)
        assertEquals(listOf("007", "008"), response.body)
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
    fun `get assistant md`() {
        doReturn(
            listOf(createBootstrap("007", instructions = "You are 007"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/assistant.md", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("You are 007", response.body!!["content"])
    }

    @Test
    fun `get assistant md returns empty content when no instructions`() {
        doReturn(
            listOf(createBootstrap("007", instructions = null))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/assistant.md", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("", response.body!!["content"])
    }

    @Test
    fun `get assistant md not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/assistant.md", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `save assistant md`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/assistant.md",
            mapOf("content" to "New identity"),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap.getContext().assistant).saveInstructions("New identity")
    }

    @Test
    fun `save assistant md with missing content saves empty string`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/assistant.md",
            emptyMap<String, Any>(),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap.getContext().assistant).saveInstructions("")
    }

    @Test
    fun `save assistant md not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/xxx/assistant.md",
            mapOf("content" to "New identity"),
            Map::class.java
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get heartbeat md`() {
        doReturn(
            listOf(createBootstrap("007", heartbeatInstructions = "Tick every hour"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/heartbeat.md", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Tick every hour", response.body!!["content"])
    }

    @Test
    fun `get heartbeat md returns empty content when no instructions`() {
        doReturn(
            listOf(createBootstrap("007", heartbeatInstructions = null))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/heartbeat.md", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("", response.body!!["content"])
    }

    @Test
    fun `get heartbeat md not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/heartbeat.md", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `save heartbeat md`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/heartbeat.md",
            mapOf("content" to "Run every minute"),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap.getContext().heartbeat).saveInstructions("Run every minute")
    }

    @Test
    fun `save heartbeat md with missing content saves empty string`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/heartbeat.md",
            emptyMap<String, Any>(),
            Map::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap.getContext().heartbeat).saveInstructions("")
    }

    @Test
    fun `save heartbeat md not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.exchange(
            "/assistants/xxx/heartbeat.md",
            HttpMethod.POST,
            HttpEntity(mapOf("content" to "Run every minute")),
            Map::class.java
        )

        assertEquals(404, response.statusCode.value())
    }

    private fun createBootstrap(
        name: String,
        description: String? = null,
        contextLength: Int = 1024,
        instructions: String? = null,
        heartbeatInstructions: String? = null,
    ): Bootstrap {
        val llm = mock<LLM>()
        doReturn(MAX_CONTEXT_WINDOW).whenever(llm).maxContextWindow()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name
        doReturn(description).whenever(assistant).description
        doReturn(contextLength).whenever(assistant).contextLength(anyOrNull(), anyOrNull())
        doReturn(instructions).whenever(assistant).getInstructions()

        val heartbeat = mock<Heartbeat>()
        doReturn(heartbeatInstructions).whenever(heartbeat).getInstructions()

        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/$name"),
            llm = llm,
            heartbeat = heartbeat,
        )
        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
