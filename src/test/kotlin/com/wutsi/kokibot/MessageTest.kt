package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMUsage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageTest {
    @Test
    fun `usage defaults to null`() {
        val message = Message(text = "hello")
        assertNull(message.usage)
    }

    @Test
    fun `usage is preserved when set`() {
        val usage = LLMUsage(totalTokens = 30, promptTokens = 10, completionTokens = 20)
        val message = Message(text = "hello", usage = usage)
        assertEquals(usage, message.usage)
    }
}
