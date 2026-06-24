package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.memory.Memory
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
class MemoryControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun `get memory`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/memory", Map::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(true, body["enabled"])
        assertEquals(7, body["window"])
        assertEquals(10240, body["maxLength"])
        assertEquals("6h", body["frequency"])
    }

    @Test
    fun `get memory - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/memory", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set memory setting`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/memory/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("memory.enabled", false)
    }

    @Test
    fun `set memory setting - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/xxx/memory/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set memory setting - bad request when missing key`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/memory/settings",
            mapOf("value" to false),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `set memory setting - bad request on unknown key`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(ConfigurationException("Unknown memory setting: invalid")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/memory/settings",
            mapOf("key" to "invalid", "value" to "x"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("Unknown memory setting: invalid", (response.body as Map<*, *>)["error"])
    }

    private fun createBootstrap(name: String): Bootstrap {
        val memory = mock<Memory>()
        doReturn(true).whenever(memory).isEnabled()
        doReturn(7L).whenever(memory).getWindow()
        doReturn(10240).whenever(memory).getMaxLength()
        doReturn("6h").whenever(memory).getCompactionFrequency()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val context = Context(
            assistant = assistant,
            home = File("target/memory-controller/$name"),
            llm = mock<LLM>(),
            memory = memory,
        )
        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
