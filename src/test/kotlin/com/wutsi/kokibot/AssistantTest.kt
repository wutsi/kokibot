package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.exception.CommandNotFoundException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.memory.Memory
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals

class AssistantTest {
    private val home = getResourceFile("/home/007")
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val chatHistory = mock<ChatHistory>()
    private val memory = mock<Memory>()
    private val commandRegistry = mock<CommandRegistry>()
    private val skillRegistry = mock<SkillRegistry>()
    private val context = Context(
        home = home,
        llm = llm,
        toolRegistry = toolRegistry,
        chatHistory = chatHistory,
        memory = memory,
        commandRegistry = commandRegistry,
        skillRegistry = skillRegistry,
        config = emptyMap<String, String>(),
    )
    private val assistant: Assistant = Assistant()

    private val cmd = mock<Command>()
    private val meta = CommandMetadata(name = "/tool")

    @BeforeEach
    fun setup() {
        assistant.init(emptyMap<Any, Any>(), context)

        doReturn(
            ToolMetadata(
                name = "test-tool",
                parameters = emptyList()
            )
        ).whenever(tool1).metadata()
        doReturn("Yaounde").whenever(tool1).exec(any())

        doReturn(
            ToolMetadata(
                name = "test-tool-2",
                parameters = emptyList()
            )
        ).whenever(tool2).metadata()
        doReturn("Paris").whenever(tool2).exec(any())

        doReturn(tool1).whenever(toolRegistry).get(any())
        doReturn(listOf(tool1, tool2)).whenever(toolRegistry).all()
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
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals("Query: ${prompt.text}", req.firstValue.prompt)
        assertEquals(
            "You are a system agent designed to assist users with various tasks.\n",
            req.firstValue.systemInstructions
        )

        verify(chatHistory).append(prompt, result)
    }

    @Test
    fun `process without system instruction`() {
        // GIVEN
        assistant.init(
            emptyMap<Any, Any>(),
            context = Context(
                home = getResourceFile("/home/no-system-instruction"),
                llm = llm,
                toolRegistry = toolRegistry,
                chatHistory = chatHistory,
                memory = memory,
            )
        )

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
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals("Query: ${prompt.text}", req.firstValue.prompt)
        assertEquals(null, req.firstValue.systemInstructions)

        verify(chatHistory).append(prompt, result)
    }

    @Test
    fun `process with chat history`() {
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

        val history =
            "[{\"text\":\"Hello\",\"role\":\"USER\",\"finishReason\":\"DONE\",\"exception\":null,\"dateTime\":\"2024-06-01T10:00:00\"}]"
        doReturn(history).whenever(chatHistory).get()

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))
        assertEquals(true, req.firstValue.prompt.contains(history))

        verify(chatHistory).append(prompt, result)
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

        verify(chatHistory).append(prompt, result)
    }

    @Test
    fun `process with skill activation`() {
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
                                name = "forecast-tool",
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
                        content = "The current temperature iis 25°C",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        val skill = Skill(
            metadata = SkillMetadata(
                name = "weather-skill",
                description = "Provides weather information",
                keywords = listOf("weather", "temperature", "forecast"),
                tools = listOf(
                    ToolMetadata(
                        name = "forecast-tool",
                        parameters = emptyList()
                    )
                )
            ),
        )
        skill.init(emptyMap<String, Any>(), context)
        doReturn(listOf(skill)).whenever(skillRegistry).all()

        // WHEN
        val prompt = Message("How does weather looks like today?", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The current temperature iis 25°C", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm, times(2)).completion(req.capture(), eq(listOf(tool1, tool2) + skill.getTools()))
        assertEquals("Query: ${prompt.text}", req.firstValue.prompt)
        assertEquals(
            """
                You are a system agent designed to assist users with various tasks.

                # Available skills
                ## weather-skill
                Provides weather information
            """.trimIndent(),
            req.firstValue.systemInstructions
        )

        verify(chatHistory).append(prompt, result)
    }

    @Test
    fun `process with LLM error`() {
        // GIVEN
        doThrow(RuntimeException("Failed")).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)

        verify(chatHistory).append(prompt, result)
    }

    @Test
    fun `process with Tool error`() {
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
        assertEquals(Assistant.FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)

        verify(chatHistory).append(prompt, result)
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

        verify(cmd).exec("Hello world", context)

        verify(chatHistory, never()).append(any(), any())
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

        verify(cmd).exec("", context)

        verify(chatHistory, never()).append(any(), any())
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

        verify(chatHistory, never()).append(any(), any())
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
        assertEquals(Assistant.TOO_MANY_ITERATIONS, result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.TOO_MANY_ITERATIONS, result.finishReason)

        verify(llm, times(4)).completion(any(), eq(listOf(tool1, tool2)))

        verify(chatHistory).append(prompt, result)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
