package com.wutsi.kokibot.llm.kimi

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.service.credential.CredentialService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.Mockito
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KimiTest {
    private val llm = Kimi()
    private val credentialService = mock<CredentialService>()
    private val config = mapOf(
        "model" to "kimi-k2.6",
    )
    private val context = Context(
        home = File("/target"),
        llm = Mockito.mock(),
        config = mapOf("xx" to "yy"),
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.kimi")).doReturn(System.getenv("KIMI_API_KEY") ?: "")
    }

    @Test
    fun contextLength() {
        assertEquals(256 * 1024, llm.maxContextWindow())
    }

    @Test
    fun id() {
        assertEquals("llm:kimi", llm.id())
    }

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
        assertEquals(true, choices[0].content?.contains("Driver's License", true))
        assertEquals(true, choices[0].content?.contains("353 826 386", true))
        println(choices[0].content)
    }

    @Test
    fun balance() {
        llm.init(config, context)

        val response = llm.balance()
        println(response)

        assertNotNull(response)
        assertTrue(response.total >= 0.0)
        assertEquals("USD", response.currency)
    }
}
