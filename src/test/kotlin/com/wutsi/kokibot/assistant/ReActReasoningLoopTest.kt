package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.FinishReason
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.TooManyIterationException
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.user.AskQuestionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReActReasoningLoopTest {
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val commandRegistry = mock<CommandRegistry>()
    private val sessionLog = mock<SessionLog>()
    private val context = mock<Context>()
    private val promptBuilder = mock<PromptBuilder>()
    private val toolOrchestrator = mock<ToolOrchestrator>()
    private lateinit var reasoningLoop: ReActReasoningLoop

    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()

    @BeforeEach
    fun setup() {
        doReturn(llm).whenever(context).llm
        doReturn(toolRegistry).whenever(context).toolRegistry
        doReturn(commandRegistry).whenever(context).commandRegistry
        doReturn(sessionLog).whenever(context).sessionLog

        doReturn(ToolMetadata(name = "tool1", parameters = emptyList())).whenever(tool1).metadata()
        doReturn(ToolMetadata(name = "tool2", parameters = emptyList())).whenever(tool2).metadata()
        doReturn(listOf(tool1, tool2)).whenever(toolRegistry).all()

        doReturn(false).whenever(llm).supportsStreaming()
        doReturn("Test prompt").whenever(promptBuilder).buildPrompt(any(), any(), any())
        doReturn("Test instructions").whenever(promptBuilder).buildSystemInstructions(any(), any(), any())

        reasoningLoop = ReActReasoningLoop(
            assistantName = "test-assistant",
            maxIterations = 5,
            coordinator = false,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )
    }

    @Test
    fun `should execute simple query with text response`() {
        val query = Message(text = "Hello", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Hello there!",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )
        doReturn(llmResponse).whenever(llm).completion(any(), any())

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals("Hello there!", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
        assertTrue(memory.contains("Hello there!"))
    }

    @Test
    fun `should execute query with tool calls`() {
        val query = Message(text = "What's the weather?", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse1 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Let me check the weather",
                    toolCalls = listOf(
                        LLMToolCall(id = "1", name = "tool1", arguments = mapOf<String, Any?>("location" to "Paris"))
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )

        val llmResponse2 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "The weather is sunny",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )

        doReturn(llmResponse1, llmResponse2).whenever(llm).completion(any(), any())

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals("The weather is sunny", result.text)
        verify(toolOrchestrator).executeTools(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `should throw TooManyIterationException when max iterations exceeded`() {
        val query = Message(text = "Test", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Thinking...",
                    toolCalls = listOf(
                        LLMToolCall(id = "1", name = "tool1", arguments = emptyMap<String, Any?>())
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )
        doReturn(llmResponse).whenever(llm).completion(any(), any())

        assertThrows<TooManyIterationException> {
            reasoningLoop.execute(query, null, 0, memory, context)
        }
    }

    @Test
    fun `should handle command execution`() {
        val command = mock<Command>()
        val metadata = CommandMetadata(name = "/help")
        doReturn(metadata).whenever(command).metadata()
        doReturn("Help text").whenever(command).exec(any(), any())
        doReturn(command).whenever(commandRegistry).get("/help")

        val query = Message(text = "/help", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals("Help text", result.text)
        assertEquals(Role.COMMAND, result.role)
        verify(command).exec(any(), eq(context))
    }

    @Test
    fun `should handle invalid command gracefully`() {
        doThrow(CommandNotFoundException("not found")).whenever(commandRegistry).get("/invalid")

        val query = Message(text = "/invalid", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals(Role.COMMAND, result.role)
        assertTrue(result.text.contains("Invalid command"))
    }

    @Test
    fun `should handle AskQuestionException and pause session`() {
        val query = Message(text = "Test", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Need info",
                    toolCalls = listOf(
                        LLMToolCall(id = "1", name = "tool1", arguments = emptyMap<String, Any?>())
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )
        doReturn(llmResponse).whenever(llm).completion(any(), any())
        doThrow(AskQuestionException("What is your name?")).whenever(toolOrchestrator).executeTools(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals("What is your name?", result.text)
        verify(sessionLog).pause(eq("user1"), eq("channel1"), eq(query.id))
    }

    @Test
    fun `should use coordinator instructions when coordinator mode enabled`() {
        val coordinatorLoop = ReActReasoningLoop(
            assistantName = "coordinator",
            maxIterations = 5,
            coordinator = true,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )

        val query = Message(text = "Test", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Response",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )
        doReturn(llmResponse).whenever(llm).completion(any(), any())

        coordinatorLoop.execute(query, null, 0, memory, context)

        verify(promptBuilder).buildSystemInstructions(eq(query), eq(true), eq(context))
    }

    @Test
    fun `should handle multiple iterations with tool calls`() {
        val query = Message(text = "Complex task", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()

        val llmResponse1 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Using tool1",
                    toolCalls = listOf(
                        LLMToolCall(id = "1", name = "tool1", arguments = emptyMap<String, Any?>())
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )

        val llmResponse2 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Using tool2",
                    toolCalls = listOf(
                        LLMToolCall(id = "2", name = "tool2", arguments = emptyMap<String, Any?>())
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )

        val llmResponse3 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Task complete",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )

        doReturn(llmResponse1, llmResponse2, llmResponse3).whenever(llm).completion(any(), any())

        val result = reasoningLoop.execute(query, null, 0, memory, context)

        assertEquals("Task complete", result.text)
        verify(toolOrchestrator, times(2)).executeTools(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }
}
