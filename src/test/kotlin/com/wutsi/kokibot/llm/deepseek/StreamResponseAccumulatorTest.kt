package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.llm.LLMToolCallDelta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class StreamResponseAccumulatorTest {
    private val jsonMapper: JsonMapper = JsonMapper()
    private val accumulator = StreamResponseAccumulator(jsonMapper)

    @Test
    fun `empty accumulator returns response with null content and no tool calls`() {
        val response = accumulator.toResponse()

        assertNotNull(response.id)
        assertEquals(1, response.choices.size)
        val choice = response.choices[0]
        assertEquals(0, choice.index)
        assertNull(choice.finishReason)
        assertNull(choice.content)
        assertNull(choice.reasoningContent)
        assertEquals(emptyList<LLMToolCall>(), choice.toolCalls)
    }

    @Test
    fun `accumulates content reasoning and finish reason across chunks`() {
        accumulator.add(LLMStreamChunk(delta = "Hello", reasoningDelta = "think-"))
        accumulator.add(LLMStreamChunk(delta = " world", reasoningDelta = "ing"))
        accumulator.add(LLMStreamChunk(finishReason = LLMFinishReason.STOP))

        val choice = accumulator.toResponse().choices[0]

        assertEquals("Hello world", choice.content)
        assertEquals("think-ing", choice.reasoningContent)
        assertEquals(LLMFinishReason.STOP, choice.finishReason)
    }

    @Test
    fun `merges tool call deltas grouped by index`() {
        accumulator.add(
            LLMStreamChunk(toolCallDelta = LLMToolCallDelta(index = 0, id = "c1", name = "search")),
        )
        accumulator.add(
            LLMStreamChunk(toolCallDelta = LLMToolCallDelta(index = 0, argumentsFragment = "{\"q\":")),
        )
        accumulator.add(
            LLMStreamChunk(toolCallDelta = LLMToolCallDelta(index = 0, argumentsFragment = "\"hi\"}")),
        )
        accumulator.add(
            LLMStreamChunk(
                toolCallDelta = LLMToolCallDelta(
                    index = 1,
                    id = "c2",
                    name = "ping",
                    argumentsFragment = "{}",
                ),
            ),
        )

        val toolCalls = accumulator.toResponse().choices[0].toolCalls

        assertEquals(2, toolCalls.size)
        assertEquals("search", toolCalls[0].name)
        assertEquals(mapOf("q" to "hi"), toolCalls[0].arguments)
        assertEquals("ping", toolCalls[1].name)
        assertEquals(emptyMap<String, Any>(), toolCalls[1].arguments)
    }

    @Test
    fun `complete tool call is appended at next available index`() {
        accumulator.add(
            LLMStreamChunk(
                toolCallDelta = LLMToolCallDelta(
                    index = 0,
                    id = "c1",
                    name = "search",
                    argumentsFragment = "{}",
                ),
            ),
        )
        val complete = LLMToolCall(name = "tool", arguments = mapOf("x" to 1))
        accumulator.add(LLMStreamChunk(toolCall = complete))

        val toolCalls = accumulator.toResponse().choices[0].toolCalls

        assertEquals(2, toolCalls.size)
        assertEquals("search", toolCalls[0].name)
        assertEquals("tool", toolCalls[1].name)
        assertEquals(mapOf("x" to 1), toolCalls[1].arguments)
    }

    @Test
    fun `complete tool call with empty accumulator goes to index 0`() {
        val complete = LLMToolCall(name = "tool", arguments = mapOf("x" to 1))
        accumulator.add(LLMStreamChunk(toolCall = complete))

        val toolCalls = accumulator.toResponse().choices[0].toolCalls

        assertEquals(1, toolCalls.size)
        assertEquals("tool", toolCalls[0].name)
    }

    @Test
    fun `last finish reason wins`() {
        accumulator.add(LLMStreamChunk(finishReason = LLMFinishReason.STOP))
        accumulator.add(LLMStreamChunk(finishReason = LLMFinishReason.TOOL_CALLS))

        assertEquals(LLMFinishReason.TOOL_CALLS, accumulator.toResponse().choices[0].finishReason)
    }

    @Test
    fun `each instance produces a unique response id`() {
        val id1 = StreamResponseAccumulator(jsonMapper).toResponse().id
        val id2 = StreamResponseAccumulator(jsonMapper).toResponse().id

        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals(id1, id2)
    }
}
