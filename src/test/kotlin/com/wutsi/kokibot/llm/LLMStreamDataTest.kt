package com.wutsi.kokibot.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LLMStreamDataTest {
    @Test
    fun `should create StreamData with text only`() {
        val data = LLMStreamData(text = "Hello")

        assertEquals("Hello", data.text)
        assertNull(data.usage)
    }

    @Test
    fun `should create StreamData with text and usage`() {
        val usage = LLMUsage(
            totalTokens = 100,
            promptTokens = 50,
            completionTokens = 50
        )
        val data = LLMStreamData(text = "Hello", usage = usage)

        assertEquals("Hello", data.text)
        assertEquals(usage, data.usage)
    }

    @Test
    fun `should create StreamData with empty text and usage`() {
        val usage = LLMUsage(
            totalTokens = 100,
            promptTokens = 50,
            completionTokens = 50
        )
        val data = LLMStreamData(text = "", usage = usage)

        assertEquals("", data.text)
        assertEquals(usage, data.usage)
    }
}
