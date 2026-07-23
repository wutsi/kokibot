package com.wutsi.kokibot.llm.kimi

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.deepseek.DeepseekClient
import com.wutsi.kokibot.util.RestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class KimiClientTest {
    companion object {
        const val API_KEY = "sd-xxxxxxx"
        const val MODEL = "kimi-k2.6"
    }

    private val rest = mock<RestTemplate>()
    private val restBuilder = mock<RestBuilder>()

    private val dsContentResponse = mapOf(
        "id" to "ds-id-000000000",
        "usage" to mapOf(
            "prompt_tokens" to 10,
            "completion_tokens" to 20,
            "total_tokens" to 30
        ),
        "choices" to listOf(
            mapOf(
                "finish_reason" to "stop",
                "index" to 0,
                "message" to mapOf(
                    "content" to "Hello, how can I help you?",
                    "role" to "assistant",
                    "reasoning_content" to "Thinking..."
                )
            )
        ),
        "model" to MODEL,
        "usage" to mapOf(
            "total_tokens" to 3051,
            "completion_tokens" to 2051,
            "prompt_tokens" to 1000,
            "prompt_cache_hit_tokens" to 2000,
        )
    )

    @BeforeEach
    fun setUp() {
        doReturn(rest).whenever(restBuilder).build(anyOrNull(), anyOrNull())

        doReturn(ResponseEntity(dsContentResponse, HttpStatus.OK))
            .whenever(rest)
            .postForEntity(
                eq("https://api.deepseek.com/chat/completions"),
                any<HttpEntity<*>>(),
                eq(Map::class.java)
            )
    }

    @Test
    fun toTemperature() {
        val client = createClient(temperature = 0.6)
        val request = mock<LLMRequest>()

        assertEquals(null, client.toTemperature(request))
    }

    @Test
    fun `toThinking - kimi-k26`() {
        val client = createClient(thinking = false, model = "kimi-k2.6")
        val request = mock<LLMRequest>()

        assertEquals(false, client.toThinking(request))
    }

    @Test
    fun `toThinking - kimi-k27-code`() {
        val client = createClient(thinking = false, model = "kimi-k2.7-code")
        val request = mock<LLMRequest>()

        assertEquals(true, client.toThinking(request))
    }

    @Test
    fun `toThinking - false`() {
        val client = createClient(thinking = false)
        val request = mock<LLMRequest>()

        assertEquals(false, client.toThinking(request))
    }

    @Test
    fun `toThinking - true`() {
        val client = createClient(thinking = true)
        val request = mock<LLMRequest>()

        assertEquals(true, client.toThinking(request))
    }

    private fun createClient(
        temperature: Double = 1.0,
        thinking: Boolean = true,
        reasoningEffort: String? = "max",
        model: String = MODEL,
    ): DeepseekClient {
        return KimiClient(
            apiKey = API_KEY,
            model = model,
            thinking = thinking,
            temperature = temperature,
            maxTokens = 2048,
            readTimeoutMillis = 1000,
            connectTimeoutMillis = 3000,
            restBuilder = restBuilder,
            reasoningEffort = reasoningEffort,
        )
    }
}
