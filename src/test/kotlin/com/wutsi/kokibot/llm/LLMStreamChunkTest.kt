package com.wutsi.kokibot.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LLMStreamChunkTest {
    @Test
    fun `should create chunk with content delta`() {
        val chunk = LLMStreamChunk(delta = "Hello")
        assertEquals("Hello", chunk.delta)
        assertFalse(chunk.isDone)
    }

    @Test
    fun `should create chunk with reasoning delta`() {
        val chunk = LLMStreamChunk(reasoningDelta = "Let me think...")
        assertEquals("Let me think...", chunk.reasoningDelta)
        assertNull(chunk.delta)
    }

    @Test
    fun `should mark chunk as done with finish reason`() {
        val chunk = LLMStreamChunk(
            finishReason = LLMFinishReason.STOP,
            isDone = true
        )
        assertTrue(chunk.isDone)
        assertEquals(LLMFinishReason.STOP, chunk.finishReason)
    }

    @Test
    fun `should create chunk with tool call`() {
        val toolCall = LLMToolCall(
            name = "test_tool",
            arguments = mapOf("arg1" to "value1")
        )
        val chunk = LLMStreamChunk(toolCall = toolCall)
        assertEquals("test_tool", chunk.toolCall?.name)
        assertEquals("value1", chunk.toolCall?.arguments?.get("arg1"))
    }

    @Test
    fun `should create empty chunk`() {
        val chunk = LLMStreamChunk()
        assertNull(chunk.delta)
        assertNull(chunk.reasoningDelta)
        assertNull(chunk.toolCall)
        assertNull(chunk.finishReason)
        assertFalse(chunk.isDone)
    }
}
