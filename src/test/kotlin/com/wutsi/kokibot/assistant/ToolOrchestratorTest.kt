package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.user.AskQuestionException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToolOrchestratorTest {
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val toolRegistry = mock<ToolRegistry>()
    private val sessionLog = mock<SessionLog>()
    private val channelRegistry = mock<ChannelRegistry>()
    private val context = mock<Context>()
    private lateinit var orchestrator: ToolOrchestrator

    @BeforeEach
    fun setup() {
        doReturn(sessionLog).whenever(context).sessionLog
        doReturn(toolRegistry).whenever(context).toolRegistry
        doReturn(channelRegistry).whenever(context).channelRegistry

        doReturn(ToolMetadata(name = "tool1", parameters = emptyList())).whenever(tool1).metadata()
        doReturn("result1").whenever(tool1).exec(any())
        doReturn("Using tool1").whenever(tool1).statusText(any())

        doReturn(ToolMetadata(name = "tool2", parameters = emptyList())).whenever(tool2).metadata()
        doReturn("result2").whenever(tool2).exec(any())
        doReturn("Using tool2").whenever(tool2).statusText(any())

        doReturn(tool1).whenever(toolRegistry).get("tool1")
        doReturn(tool2).whenever(toolRegistry).get("tool2")

        orchestrator = ToolOrchestrator(threadPoolSize = 4)
    }

    @AfterEach
    fun cleanup() {
        orchestrator.destroy()
    }

    @Test
    fun `should execute tools in parallel`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1")),
            LLMToolCall(id = "2", name = "tool2", arguments = mapOf("arg2" to "value2"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1, "tool2" to tool2)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        verify(tool1).exec(mapOf("arg1" to "value1"))
        verify(tool2).exec(mapOf("arg2" to "value2"))
        assertEquals(4, memory.size) // 2 tools x (usage + result)
        assertTrue(memory[0].contains("Using tool `tool1` with arguments"))
        assertTrue(memory[1].contains("result1"))
        assertTrue(memory[2].contains("Using tool `tool2` with arguments"))
        assertTrue(memory[3].contains("result2"))
    }

    @Test
    fun `should execute a single tool`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        verify(tool1).exec(mapOf("arg1" to "value1"))
        assertEquals(2, memory.size) // 1 tool x (usage + result)
        assertTrue(memory[0].contains("Using tool `tool1` with arguments"))
        assertTrue(memory[1].contains("result1"))
    }

    @Test
    fun `should handle tool errors gracefully`() {
        val errorTool = mock<Tool>()
        doReturn(ToolMetadata(name = "error-tool", parameters = emptyList())).whenever(errorTool).metadata()
        doThrow(RuntimeException("Tool failed")).whenever(errorTool).exec(any())
        doReturn("Using error-tool").whenever(errorTool).statusText(any())
        doReturn(errorTool).whenever(toolRegistry).get("error-tool")

        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "error-tool", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("error-tool" to errorTool)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        assertEquals(2, memory.size)
        assertTrue(memory[0].contains("Using tool `error-tool` with arguments"))
        assertTrue(memory[1].contains("Unexpected error while executing tool `error-tool`"))
        assertTrue(memory[1].contains("Tool failed"))
    }

    @Test
    fun `should handle empty tool calls`() {
        val memory = mutableListOf<String>()
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = emptyList(),
            memory = memory,
            tools = emptyMap(),
            query = query,
            context = context
        )

        assertEquals(0, memory.size)
    }

    @Test
    fun `should handle tool not found`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "unknown-tool", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = emptyMap(),
            query = query,
            context = context
        )

        assertEquals(2, memory.size)
        assertTrue(memory[0].contains("Using tool `unknown-tool` with arguments"))
        assertTrue(memory[1].contains("Tool `unknown-tool` not found"))
    }

    @Test
    fun `should truncate long argument values`() {
        val longValue = "a".repeat(300)
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to longValue))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        assertTrue(memory[0].contains("..."))
        assertTrue(memory[0].length < 250) // Should be truncated
    }

    @Test
    fun `should propagate AskQuestionException`() {
        val askTool = mock<Tool>()
        doReturn(ToolMetadata(name = "ask-tool", parameters = emptyList())).whenever(askTool).metadata()
        doThrow(AskQuestionException("What is your name?")).whenever(askTool).exec(any())
        doReturn("Using ask-tool").whenever(askTool).statusText(any())
        doReturn(askTool).whenever(toolRegistry).get("ask-tool")

        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "ask-tool", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("ask-tool" to askTool)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        try {
            orchestrator.executeTools(
                id = query.id,
                iteration = 1,
                assistantName = "test-assistant",
                toolCalls = toolCalls,
                memory = memory,
                tools = tools,
                query = query,
                context = context
            )
            org.junit.jupiter.api.Assertions.fail("Should have thrown AskQuestionException")
        } catch (e: AskQuestionException) {
            assertEquals("What is your name?", e.question)
        }
    }

    @Test
    fun `should handle tool arguments with null values`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to null, "arg2" to "value2"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        // When value is null, it's filtered out by the map
        assertTrue(memory[0].contains("Using tool `tool1` with arguments"))
        assertTrue(memory[0].contains("arg2=value2"))
        assertEquals(2, memory.size)
    }

    @Test
    fun `should handle query without userId or channelId`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1)
        val query = Message(id = "test-id") // No userId or channelId

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        verify(tool1).exec(mapOf("arg1" to "value1"))
        assertEquals(2, memory.size)
    }
}
