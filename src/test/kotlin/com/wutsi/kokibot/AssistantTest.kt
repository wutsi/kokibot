package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.user.AskQuestionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.io.File
import java.util.UUID

class AssistantTest {
    private val home = getResourceFile("/home/007")
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val tool3 = mock<Tool>()
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val memory = mock<Memory>()
    private val commandRegistry = mock<CommandRegistry>()
    private val skillRegistry = mock<SkillRegistry>()
    private val dailyLog = mock<DailyLog>()
    private val sessionLog = mock<SessionLog>()
    private val assistantRegistry = mock<AssistantRegistry>()
    private val chatHistory = mock<ChatHistory>()
    private val context = Context(
        home = home,
        llm = llm,
        toolRegistry = toolRegistry,
        memory = memory,
        commandRegistry = commandRegistry,
        skillRegistry = skillRegistry,
        dailyLog = dailyLog,
        sessionLog = sessionLog,
        chatHistory = chatHistory,
        assistantRegistry = assistantRegistry,
        config = emptyMap<String, String>(),
    )
    private val assistant: Assistant = Assistant()

    private val cmd = mock<Command>()
    private val meta = CommandMetadata(name = "/tool")

    @BeforeEach
    fun setup() {
        doReturn(65536).whenever(llm).maxContextWindow()
        context.conversationRepository.init(emptyMap<Any, Any>(), context)
        assistant.init(emptyMap<Any, Any>(), context)

        doReturn(true).whenever(tool1).activate()
        doReturn(ToolMetadata(name = "test-tool")).whenever(tool1).metadata()
        doReturn("Yaounde").whenever(tool1).exec(any())

        doReturn(true).whenever(tool2).activate()
        doReturn(ToolMetadata(name = "test-tool-2")).whenever(tool2).metadata()
        doReturn("Paris").whenever(tool2).exec(any())

        doReturn(false).whenever(tool3).activate()
        doReturn(ToolMetadata(name = "test-tool3")).whenever(tool3).metadata()

        doReturn(tool1).whenever(toolRegistry).get(any())
        doReturn(listOf(tool1, tool2, tool3)).whenever(toolRegistry).all()

        doReturn("conv-test-123").whenever(chatHistory).append(any(), any())

        val skill1 = mock<Skill>()
        doReturn(Health(up = true, id = "xxx")).whenever(skill1).health()
        doReturn(
            SkillMetadata(
                name = "skill1",
                description = "Test skill",
                home = File("/target"),
            )
        ).whenever(skill1).metadata
        doReturn(listOf(skill1)).whenever(skillRegistry).all()
    }

    @Test
    fun contextWindow() {
        val window = assistant.contextWindow("anonymous", "channel:telegram")

        assertTrue(window.baseline > 0)
        assertTrue(window.max > 0)
    }

    @Test
    fun `contextWindow with conversationId`() {
        val window = assistant.contextWindow("anonymous", "channel:telegram", "conv-123")

        assertTrue(window.baseline > 0)
        assertTrue(window.max > 0)
    }

    @Test
    fun init() {
        // WHEN
        // init() called in setup()

        // THEN
        verify(assistantRegistry).register(assistant)
    }

    @Test
    fun process() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("Yo", Role.USER, userId = "user-1", channelId = "channel:telegram")
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()

        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))

        val systemInstructions = req.firstValue.systemInstructions
        assertSystemInstructionsContain(
            systemInstructions,
            "You are a system agent designed to assist users with various tasks.\n",
            "# Security Guidelines",
            "# Daily Log Protocol",
            "# Available skills"
        )
        // Conversation history is injected into the prompt only when conversationId is set
        assertEquals(false, req.firstValue.prompt.contains("# Conversation History"))
    }

    @Test
    fun `process with tool call`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "The capital of Cameroon is Yaounde",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The capital of Cameroon is Yaounde", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(llm, times(2)).completion(any(), eq(listOf(tool1, tool2)))
    }

    @Test
    fun `process with human in the loop`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        doThrow(AskQuestionException("Can you clarify for which country?")).whenever(tool1).exec(any())

        // WHEN
        val prompt = Message("What is the capital of the country", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Can you clarify for which country?", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `process with LLM error`() {
        // GIVEN
        doThrow(RuntimeException("Failed")).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.ERROR_FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)
    }

    @Test
    fun `process with Tool error - ignore error`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "The capital of Cameroon is Yaounde",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        doThrow(RuntimeException("Failed")).whenever(tool1).exec(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The capital of Cameroon is Yaounde", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `process execute command`() {
        // GIVEN
        doReturn(meta).whenever(cmd).metadata()
        doReturn(cmd).whenever(commandRegistry).get(any())
        doReturn("Command executed").whenever(cmd).exec(any(), any())

        // WHEN
        val prompt = Message("/tool Hello world", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Command executed", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(cmd).exec(any(), any())
    }

    @Test
    fun `process execute command without arguments`() {
        // GIVEN
        doReturn(meta).whenever(cmd).metadata()
        doReturn(cmd).whenever(commandRegistry).get(any())
        doReturn("Command executed").whenever(cmd).exec(any(), any())

        // WHEN
        val prompt = Message("/tool", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Command executed", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(cmd).exec(any(), any())
    }

    @Test
    fun `process execute invalid command`() {
        // GIVEN
        doThrow(CommandNotFoundException::class).whenever(commandRegistry).get(any())

        // WHEN
        val prompt = Message("/tool Hello world", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Invalid command: /tool.\nUse /help to get the list of available commands.", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `too many iterations`() {
        // GIVEN
        assistant.init(mapOf("max-iterations" to 3), context)

        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.ERROR_TOO_MANY_ITERATIONS, result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.TOO_MANY_ITERATIONS, result.finishReason)

        verify(llm, times(4)).completion(any(), eq(listOf(tool1, tool2)))
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }

    private fun assertSystemInstructionsContain(systemInstructions: String?, vararg sections: String) {
        sections.forEach { section ->
            assertEquals(
                true,
                systemInstructions?.contains(section),
                "Expected system instructions to contain: $section"
            )
        }
    }

    private fun assertSystemInstructionsDoNotContain(systemInstructions: String?, vararg sections: String) {
        sections.forEach { section ->
            assertEquals(
                false,
                systemInstructions?.contains(section),
                "Expected system instructions to NOT contain: $section"
            )
        }
    }
}
