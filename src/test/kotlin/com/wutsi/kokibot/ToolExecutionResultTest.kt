package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMToolCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToolExecutionResultTest {
    @Test
    fun `should create successful result`() {
        val call = LLMToolCall(name = "test-tool", id = "call-123")
        val result = ToolExecutionResult(
            call = call,
            result = "success output",
            error = null
        )

        assertEquals("test-tool", result.call.name)
        assertEquals("success output", result.result)
        assertNull(result.error)
    }

    @Test
    fun `should create error result`() {
        val call = LLMToolCall(name = "test-tool", id = "call-456")
        val error = Exception("tool failed")
        val result = ToolExecutionResult(
            call = call,
            result = "",
            error = error
        )

        assertEquals("test-tool", result.call.name)
        assertEquals("", result.result)
        assertEquals("tool failed", result.error?.message)
    }
}
