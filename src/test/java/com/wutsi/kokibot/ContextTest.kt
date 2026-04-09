package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.Channel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File

class ContextTest {
    val home = getResourceFile("/home/007")

    val channel = mock<Channel>()

    val context = Context(
        home = getResourceFile("/home/007"),
        config = emptyMap<String, String>(),
        llm = mock(),
        toolRegistry = mock(),
        channelFactory = mock(),
        chatHistory = mock(),
        memory = mock(),
    )

    private val llmConfig = mapOf("type" to "gpt-3.5-turbo")
    private val memoryConfig = mapOf("window" to 1)
    private val channelConfig = mapOf("type" to "foo")
    private val config = mapOf(
        "foo" to "bar",
        "llm" to llmConfig,
        "memory" to memoryConfig,
        "channels" to listOf(
            channelConfig
        )
    )
    private val assistant = mock<Assistant>()

    @BeforeEach
    fun setUp() {
        doReturn(channel).whenever(context.channelFactory).create(any(), any())
    }

    @Test
    fun destroy() {
        // GIVEN
        context.init(assistant, config)

        // THEN
        context.destroy()

        verify(context.llm).destroy()
        verify(context.toolRegistry).destroy()
        verify(context.memory).destroy()
        verify(context.chatHistory).destroy()
        verify(channel).destroy()
    }

    @Test
    fun init() {
        context.init(assistant, config)

        verify(context.toolRegistry).init(context)
        verify(context.llm).init(llmConfig, context)
        verify(context.memory).init(memoryConfig, context)
        verify(context.chatHistory).init(memoryConfig, context)
        verify(channel).init(channelConfig)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
