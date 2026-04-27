package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMStreamChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper

class DeepseekStreamingTest {
    @Test
    fun `deepseek should support streaming when enabled`() {
        val context = mock<Context>(Context::class.java)
        doReturn(JsonMapper()).whenever(context).jsonMapper

        val deepseek = Deepseek()
        deepseek.init(
            mapOf(
                "api-key" to "test-key",
                "model" to "deepseek-chat",
                "streaming" to true
            ),
            context
        )

        assertTrue(deepseek.streamingEnabled)
    }

    @Test
    fun `deepseek should not stream when disabled`() {
        val context = mock<Context>(Context::class.java)
        doReturn(JsonMapper()).whenever(context).jsonMapper

        val deepseek = Deepseek()
        deepseek.init(
            mapOf(
                "api-key" to "test-key",
                "model" to "deepseek-chat",
                "streaming" to false
            ),
            context
        )

        assertTrue(!deepseek.streamingEnabled)
    }

    @Test
    fun `deepseek should call client completionStream`() {
        val context = mock<Context>(Context::class.java)
        doReturn(JsonMapper()).whenever(context).jsonMapper

        val mockClient = mock<DeepseekClient>(DeepseekClient::class.java)
        val expectedResponse = LLMResponse(
            id = "test",
            choices = listOf(
                LLMResponseChoice(
                    content = "Test response"
                )
            )
        )
        doReturn(expectedResponse).whenever(mockClient).completionStream(any(), any(), any())

        val deepseek = object : Deepseek() {
            init {
                this.client = mockClient
                this.streamingEnabled = true
            }
        }

        val chunks = mutableListOf<LLMStreamChunk>()
        val response = deepseek.completionStream(
            LLMRequest(prompt = "Test"),
            emptyList()
        ) { chunk -> chunks.add(chunk) }

        assertEquals("Test response", response.choices[0].content)
    }
}
