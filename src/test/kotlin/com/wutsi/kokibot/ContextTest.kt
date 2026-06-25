package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.marketplace.Marketplace
import com.wutsi.kokibot.mcp.McpServer
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
        assistant = mock(),
        llm = mock(),
        toolRegistry = mock(),
        channelRegistry = mock(),
        dailyLog = mock(),
        sessionLog = mock(),
        commandRegistry = mock(),
        skillRegistry = mock(),
        marketplaceRegistry = mock(),
        mcpRegistry = mock(),
        memory = mock(),
        fileService = mock(),
        heartbeat = mock(),
        chatHistory = mock(),
        conversationRepository = mock(),
        knowledgeBase = mock(),
    )

    private val llmConfig = mapOf("type" to "gpt-3.5-turbo")
    private val memoryConfig = mapOf("window" to 1)
    private val assistantConfig = mapOf("x" to "y")
    private val heartbeatConfig = mapOf("p" to "q")
    private val kbConfig = mapOf("r" to "s")
    private val config = mapOf(
        "foo" to "bar",
        "llm" to llmConfig,
        "memory" to memoryConfig,
        "assistant" to assistantConfig,
        "heartbeat" to heartbeatConfig,
        "knowledge-base" to kbConfig,
    )

    @BeforeEach
    fun setUp() {
        doReturn(Health(id = "-", up = true)).whenever(marketplace).health()
        doReturn(listOf(marketplace)).whenever(context.marketplaceRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(channel).health()
        doReturn(listOf(channel)).whenever(context.channelRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(context.mcpRegistry).health()
        doReturn(emptyList<McpServer>()).whenever(context.mcpRegistry).all()

        doReturn(Health(id = "-", up = true)).whenever(context.llm).health()
        doReturn(Health(id = "-", up = true)).whenever(context.dailyLog).health()
        doReturn(Health(id = "-", up = true)).whenever(context.sessionLog).health()
        doReturn(Health(id = "-", up = true)).whenever(context.memory).health()
        doReturn(Health(id = "-", up = true)).whenever(context.chatHistory).health()
        doReturn(Health(id = "-", up = true)).whenever(context.conversationRepository).health()
        doReturn(Health(id = "-", up = true)).whenever(context.fileService).health()
        doReturn(Health(id = "-", up = true)).whenever(context.heartbeat).health()
        doReturn(Health(id = "-", up = true)).whenever(context.knowledgeBase).health()
    }

    @Test
    fun destroy() {
        // GIVEN
        context.init(config)

        // THEN
        context.destroy()

        verify(context.assistant).destroy()
        verify(context.llm).destroy()
        verify(context.memory).destroy()
        verify(context.dailyLog).destroy()
        verify(context.sessionLog).destroy()
        verify(context.fileService).destroy()
        verify(context.chatHistory).destroy()
        verify(context.conversationRepository).destroy()
        verify(context.heartbeat).destroy()
        verify(channel).destroy()
        verify(context.knowledgeBase).destroy()
        verify(context.memory).destroy()
        verify(context.dailyLog).destroy()
        verify(context.chatHistory).destroy()
    }

    @Test
    fun init() {
        context.init(config)

        verify(context.assistant).init(assistantConfig, context)
        verify(context.toolRegistry).init(context)
        verify(context.llm).init(llmConfig, context)
        verify(context.memory).init(memoryConfig, context)
        verify(context.dailyLog).init(memoryConfig, context)
        verify(context.sessionLog).init(memoryConfig, context)
        verify(context.conversationRepository).init(memoryConfig, context)
        verify(context.heartbeat).init(heartbeatConfig, context)
        verify(context.commandRegistry).init(context)
        verify(context.skillRegistry).init(context)
        verify(context.marketplaceRegistry).init(context)
        verify(context.mcpRegistry).init(emptyMap<String, Any>(), context)
        verify(context.channelRegistry).init(context)
        verify(context.fileService).init(emptyMap<String, Any>(), context)
        verify(context.knowledgeBase).init(kbConfig, context)
    }

    @Test
    fun `init - LLM missing configuration`() {
        val cfg = config - "llm"

        context.init(cfg)

        verify(context.llm, never()).init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `init - bad structure`() {
        doThrow(RuntimeException()).whenever(context.llm).init(any(), any())

        context.init(config)
    }

    @Test
    fun health() {
        // GIVEN
        context.init(config)

        // WHEN
        val health = context.health()

        // THEN
        assertTrue(health.up)
        assertEquals(
            12,
            health.children.size
        ) // LLM, Memory, DailyLog, SessionLog, ChatHistory, ConversationRepository, FileService, Heartbeat, DelegationStack, 2 channels
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
