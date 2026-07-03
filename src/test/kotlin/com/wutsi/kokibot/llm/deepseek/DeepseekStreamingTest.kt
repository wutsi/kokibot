package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.service.credential.CredentialService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DeepseekStreamingTest {
    private val credentialService = mock<CredentialService>()
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.deepseek")).doReturn(System.getenv("KOKIBOT_DEEPSEEK_API_KEY") ?: "")
    }

    @Test
    fun `deepseek should support streaming when enabled`() {
        val deepseek = Deepseek()
        deepseek.init(
            mapOf(
                "model" to "deepseek-chat",
                "streaming" to true
            ),
            context
        )

        assertTrue(deepseek.streamingEnabled)
    }

    @Test
    fun `deepseek should not stream when disabled`() {
        val deepseek = Deepseek()
        deepseek.init(
            mapOf(
                "model" to "deepseek-chat",
                "streaming" to false
            ),
            context
        )

        assertTrue(!deepseek.streamingEnabled)
    }

    @Test
    fun `deepseek should call client completionStream`() {
        val mockClient = mock<DeepseekClient>()
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
                this.streamingEnabled = true
            }

            override fun createClient(): DeepseekClient {
                return mockClient
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
