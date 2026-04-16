package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.Channel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTest {
    val home = getResourceFile("/home/007")

    val channel = mock<Channel>()

    val context = Context(
        home = getResourceFile("/home/007"),
        config = emptyMap<String, String>(),
        llm = mock(),
        toolRegistry = mock(),
        channelRegistry = mock(),
        chatHistory = mock(),
        commandRegistry = mock(),
        skillRegistry = mock(),
        memory = mock(),
        imap = mock(),
        smtp = mock(),
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
        doReturn(Health(id = "-", up = true)).whenever(channel).health()
        doReturn(listOf(channel)).whenever(context.channelRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(context.imap).health()
        doReturn(Health(id = "-", up = true)).whenever(context.llm).health()
        doReturn(Health(id = "-", up = true)).whenever(context.smtp).health()
        doReturn(Health(id = "-", up = true)).whenever(context.chatHistory).health()
        doReturn(Health(id = "-", up = true)).whenever(context.memory).health()
    }

    @Test
    fun destroy() {
        // GIVEN
        context.init(assistant, config)

        // THEN
        context.destroy()

        verify(context.llm).destroy()
        verify(context.memory).destroy()
        verify(context.chatHistory).destroy()
        verify(channel).destroy()
        verify(context.smtp).destroy()
        verify(context.imap).destroy()
    }

    @Test
    fun init() {
        context.init(assistant, config)

        verify(context.toolRegistry).init(context)
        verify(context.llm).init(llmConfig, context)
        verify(context.memory).init(memoryConfig, context)
        verify(context.chatHistory).init(memoryConfig, context)
        verify(context.commandRegistry).init(context)
        verify(context.skillRegistry).init(context)
        verify(context.smtp).init(smtpConfig, context)
        verify(context.imap).init(imapConfig, context)
        verify(context.channelRegistry).init(config, context, assistant)
    }

    @Test
    fun `init - LLM missing configuration`() {
        val cfg = config - "llm"

        context.init(assistant, cfg)

        verify(context.llm, never()).init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `init - LLM configuration erorr`() {
        doThrow(RuntimeException()).whenever(context.llm).init(any(), any())

        context.init(assistant, config)
    }

    @Test
    fun `init - mail missing configuration`() {
        val cfg = config - "mail"

        context.init(assistant, cfg)

        verify(context.smtp, never()).init(any(), any())
        verify(context.imap, never()).init(any(), any())
    }

    @Test
    fun `init - mail missing IMAP`() {
        val cfg = config - "mail" + ("mail" to mapOf("smtp" to smtpConfig))

        context.init(assistant, cfg)

        verify(context.smtp).init(smtpConfig, context)
        verify(context.imap, never()).init(any(), any())
    }

    @Test
    fun `init - mail missing SMTP`() {
        val cfg = config - "mail" + ("mail" to mapOf("imap" to imapConfig))

        context.init(assistant, cfg)

        verify(context.smtp, never()).init(any(), any())
        verify(context.imap).init(imapConfig, context)
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
