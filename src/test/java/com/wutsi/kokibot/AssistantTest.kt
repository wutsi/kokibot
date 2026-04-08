package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.memory.ChatHistory
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
    private val tool = mock<Tool>()
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val chatHistory = mock<ChatHistory>()
    private val context = Context(home, llm, toolRegistry, chatHistory, emptyMap<String, String>())
    private val assistant: Assistant = Assistant()

    @BeforeEach
    fun setup() {
        assistant.init(emptyMap<Any, Any>(), context)

        doReturn(
            ToolMetadata(
                name = "test-tool",
                parameters = emptyList()
            )
        ).whenever(tool).metadata()
        doReturn("Yaounde").whenever(tool).exec(any())

        doReturn(tool).whenever(toolRegistry).get(any())
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
        ).whenever(llm).completion(any())

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture())
        assertEquals("Query: ${prompt.text}", req.firstValue.prompt)
        assertEquals(
            "You are a system agent designed to assist users with various tasks.\n",
            req.firstValue.systemInstructions
        )

        verify(chatHistory).save(prompt, result)
    }

    @Test
    fun `process without system instruction`() {
        // GIVEN
        assistant.init(
            emptyMap<Any, Any>(),
            context.copy(home = getResourceFile("/home/no-system-instruction"))
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
        ).whenever(llm).completion(any())

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture())
        assertEquals("Query: ${prompt.text}", req.firstValue.prompt)
        assertEquals(null, req.firstValue.systemInstructions)

        verify(chatHistory).save(prompt, result)
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
        ).whenever(llm).completion(any())

        val history =
            "[{\"text\":\"Hello\",\"role\":\"USER\",\"finishReason\":\"DONE\",\"exception\":null,\"dateTime\":\"2024-06-01T10:00:00\"}]"
        doReturn(history).whenever(chatHistory).loadJson()

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture())
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))
        assertEquals(true, req.firstValue.prompt.contains(history))

        verify(chatHistory).save(prompt, result)
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
        ).whenever(llm).completion(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The capital of Cameroon is Yaounde", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(llm, times(2)).completion(any())

        verify(chatHistory).save(prompt, result)
    }

    @Test
    fun `process with LLM error`() {
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
        ).whenever(llm).completion(any())

        doThrow(RuntimeException("Failed")).whenever(llm).completion(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)

        verify(chatHistory).save(prompt, result)
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
        ).whenever(llm).completion(any())

        doThrow(RuntimeException("Failed")).whenever(tool).exec(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)

        verify(chatHistory).save(prompt, result)
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
        ).whenever(llm).completion(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.TOO_MANY_ITERATIONS, result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.TOO_MANY_ITERATIONS, result.finishReason)

        verify(llm, times(4)).completion(any())

        verify(chatHistory).save(prompt, result)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
