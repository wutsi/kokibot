package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
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
    fun assistant() {
        doReturn(
            listOf(createBootstrap("007", description = "Hello world"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Hello world", response.body!!["description"])
    }

    @Test
    fun `assistant not found`() {
        doReturn(
            listOf(createBootstrap("007"))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun contextLength() {
        doReturn(
            listOf(createBootstrap("007", contextLength = 333))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/context-length", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(333, response.body!!["value"])
        assertEquals(MAX_CONTEXT_WINDOW, response.body!!["max"])
    }

    @Test
    fun `contextLength - not-found`() {
        doReturn(
            listOf(createBootstrap("007", contextLength = 333))
        ).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxxx/context-length", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    private fun createBootstrap(name: String, description: String? = null, contextLength: Int = 1024): Bootstrap {
        val llm = mock<LLM>()
        doReturn(MAX_CONTEXT_WINDOW).whenever(llm).maxContextLength()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name
        doReturn(description).whenever(assistant).description
        doReturn(contextLength).whenever(assistant).contextLength()

        val context = Context(
            assistant = assistant,
            home = File("target/assistant-controller/$name"),
            llm = llm,
        )
        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
