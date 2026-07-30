package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.io.File

class ParallelToolExecutionIntegrationTest {
    private lateinit var assistant: Assistant
    private lateinit var context: Context
    private val toolRegistry = mock<ToolRegistry>()
    private val llm = mock<LLM>()

    @BeforeEach
    fun setup() {
        // Create slow tools that simulate network calls
        val slowTool1 = createSlowTool("weather-tool", delayMs = 500)
        val slowTool2 = createSlowTool("news-tool", delayMs = 500)
        val slowTool3 = createSlowTool("stock-tool", delayMs = 500)
        doReturn(listOf(slowTool1, slowTool2, slowTool3)).whenever(toolRegistry).all()
        doReturn(slowTool1).whenever(toolRegistry).get("weather-tool")
        doReturn(slowTool2).whenever(toolRegistry).get("news-tool")
        doReturn(slowTool3).whenever(toolRegistry).get("stock-tool")

        context = Context(
            home = File(System.getProperty("java.io.tmpdir"), "kokibot-test-parallel"),
            llm = llm,
            toolRegistry = toolRegistry,
            memory = mock<Memory>(),
            commandRegistry = mock<CommandRegistry>(),
            skillRegistry = mock<SkillRegistry>(),
            dailyLog = mock<DailyLog>(),
            sessionLog = mock<SessionLog>(),
            chatHistory = mock<ChatHistory>(),
            assistantRegistry = mock<AssistantRegistry>(),
        )

        context.inbox.init(emptyMap<Any, Any>(), context)

        assistant = Assistant("test-parallel")
        assistant.init(mapOf("thread-pool-size" to 4), context)
    }

    @AfterEach
    fun tearDown() {
        assistant.destroy()
    }

    @Test
    fun `parallel execution should be faster than sequential would be`() {
        val query = Message(text = "test parallel perf", userId = "user-1", channelId = "channel-1")

        // LLM returns 3 tool calls
        val response1 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = null,
                    toolCalls = listOf(
                        LLMToolCall(name = "weather-tool", id = "call-1"),
                        LLMToolCall(name = "news-tool", id = "call-2"),
                        LLMToolCall(name = "stock-tool", id = "call-3")
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )
        val response2 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "All results collected",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )

        doReturn(response1).doReturn(response2).whenever(llm).completion(any(), any())

        val startTime = System.currentTimeMillis()
        val result = assistant.process(query)
        val duration = System.currentTimeMillis() - startTime

        assertEquals("All results collected", result.text)

        // 3 tools × 500ms = 1500ms sequentially
        // With parallel execution (3 threads): ~500ms + overhead
        // Should complete in less than 1000ms
        assertTrue(duration < 1000, "Parallel execution took ${duration}ms, expected < 1000ms")
    }

    private fun createSlowTool(name: String, delayMs: Long): Tool {
        val tool = mock<Tool>()
        doReturn(ToolMetadata(name = name, parameters = emptyList())).whenever(tool).metadata()
        doAnswer { invocation ->
            Thread.sleep(delayMs)
            "Result from $name"
        }.whenever(tool).exec(any())
        return tool
    }
}
