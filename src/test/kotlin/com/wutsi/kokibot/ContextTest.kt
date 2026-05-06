package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.marketplace.Marketplace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTest {
    val home = getResourceFile("/home/007")

    val channel = mock<Channel>()
    val marketplace = mock<Marketplace>()

    val context = Context(
        home = getResourceFile("/home/007"),
        config = emptyMap<String, String>(),
        llm = mock(),
        toolRegistry = mock(),
        channelRegistry = mock(),
        dailyLog = mock(),
        commandRegistry = mock(),
        skillRegistry = mock(),
        marketplaceRegistry = mock(),
        memory = mock(),
        fileService = mock(),
    )

    private val llmConfig = mapOf("type" to "gpt-3.5-turbo")
    private val memoryConfig = mapOf("window" to 1)
    private val channelConfig = listOf(mapOf("type" to "foo"))
    private val smtpConfig = mapOf("smtp" to "foo")
    private val imapConfig = mapOf("imap" to "foo")
    private val config = mapOf(
        "foo" to "bar",
        "llm" to llmConfig,
        "memory" to memoryConfig,
        "channels" to listOf(
            channelConfig
        ),
        "mail" to mapOf(
            "smtp" to smtpConfig,
            "imap" to imapConfig,
        )
    )
    private val assistant = mock<Assistant>()

    @BeforeEach
    fun setUp() {
        doReturn(Health(id = "-", up = true)).whenever(marketplace).health()
        doReturn(listOf(marketplace)).whenever(context.marketplaceRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(channel).health()
        doReturn(listOf(channel)).whenever(context.channelRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(context.llm).health()
        doReturn(Health(id = "-", up = true)).whenever(context.dailyLog).health()
        doReturn(Health(id = "-", up = true)).whenever(context.memory).health()
        doReturn(Health(id = "-", up = true)).whenever(context.fileService).health()
    }

    @Test
    fun destroy() {
        // GIVEN
        context.init(assistant, config)

        // THEN
        context.destroy()

        verify(context.llm).destroy()
        verify(context.memory).destroy()
        verify(context.dailyLog).destroy()
        verify(context.fileService).destroy()
        verify(channel).destroy()
    }

    @Test
    fun init() {
        context.init(assistant, config)

        verify(context.toolRegistry).init(context)
        verify(context.llm).init(llmConfig, context)
        verify(context.memory).init(memoryConfig, context)
        verify(context.dailyLog).init(memoryConfig, context)
        verify(context.commandRegistry).init(context)
        verify(context.skillRegistry).init(context)
        verify(context.channelRegistry).init(config, context, assistant)
        verify(context.fileService).init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `init - LLM missing configuration`() {
        val cfg = config - "llm"

        context.init(assistant, cfg)

        verify(context.llm, never()).init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `init - bad structure`() {
        doThrow(RuntimeException()).whenever(context.llm).init(any(), any())

        context.init(assistant, config)
    }

    @Test
    fun health() {
        // GIVEN
        context.init(assistant, config)

        // WHEN
        val health = context.health()

        // THEN
        assertTrue(health.up)
        assertEquals(6, health.children.size)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
