package com.wutsi.kokibot.llm.kimi

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class KimiTest {
    private val llm = Kimi()
    private val config = mapOf(
        "api-key" to System.getenv("KIMI_API_KEY"),
        "model" to "kimi-k2.6",
    )
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        config = mapOf("xx" to "yy"),
    )

    @Test
    fun completion() {
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

    @Test
    fun `completion with image file`() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you describe this image?",
                files = listOf(
                    File(this::class.java.getResource("/deepseek/sample.jpg")!!.file)
                )
            ),
            tools = emptyList()
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(0, choices[0].toolCalls.size)
        assertEquals(false, choices[0].content.isNullOrEmpty())
        println(choices[0].content)
    }

    @Test
    fun contextLength() {
        assertEquals(1024 * 1024, llm.maxContextLength())
    }
}
