package com.wutsi.kokibot.llm.none

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NullLLMTest {
    private val llm = NullLLM()

    @Test
    fun id() {
        assertEquals("llm", llm.id())
    }

    @Test
    fun completion() {
        llm.init(Mockito.mock<Map<*, *>>(), Mockito.mock<Context>())
        val response = llm.completion(
            request = LLMRequest(prompt = "What is the capital of France?"),
            emptyList(),
        )
        // println(response)

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(NullLLM.Companion.MESSAGE, choices[0].content)
        assertEquals(true, choices[0].toolCalls.isEmpty())
    }

    @Test
    fun health() {
        val health = llm.health()
        assertFalse(health.up)
        assertEquals(llm.id(), health.id)
    }

    @Test
    fun maxContextLength() {
        assertEquals(0, llm.maxContextLength())
    }
}
