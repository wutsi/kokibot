package com.wutsi.kokibot.llm.gemini

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class GeminiTest {
    private val llm = Gemini()
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        config = mapOf("xx" to "yy"),
    )

    @Test
    fun completion() {
        val config = mapOf(
            "api_key" to System.getenv("GEMINI_API_KEY"),
            "model" to "gemini-2.5-flash-lite",
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(prompt = "What is the capital of France?"),
            emptyList(),
        )
        // println(response)

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(true, choices[0].content?.contains("Paris"))
        assertEquals(true, choices[0].toolCalls.isEmpty())
    }
}
