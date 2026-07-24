package com.wutsi.kokibot.llm.kimi

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.service.credential.CredentialService
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
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
        "temperature" to 0.7,
        "maxTokens" to 1024,
        "thinking" to false,
        "reasoning-effort" to "standard",
    )
    private val context = Context(
        home = File("/target"),
        llm = Mockito.mock(),
        config = mapOf("xx" to "yy"),
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.kimi")).doReturn(System.getenv("KOKIBOT_KIMI_API_KEY") ?: "")
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
    fun `completion - thinking=true`() {
        val config = mapOf(
            "model" to "kimi-k2.6",
            "thinking" to true,
            "reasoning-effort" to "high",
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

    @Test
    fun `completion - kimi-k2_7-code`() {
        val config = mapOf(
            "model" to "kimi-k2.7-code",
            "thinking" to false,
            "reasoning-effort" to "standard",
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = """
                What's the bug on this code snippet?
                ```kotlin
                fun main() {
                    val x = 10
                    val y = 0
                    val z = x / y
                    println(z)
                }
                ```
            """.trimIndent()
            ),
            emptyList(),
        )
        println(response)

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(true, choices[0].content?.contains("by zero"))
        assertEquals(true, choices[0].toolCalls.isEmpty())
    }

    @Test
    fun `completion - kimi-k3`() {
        val config = mapOf(
            "model" to "kimi-k3",
            "thinking" to true,
            "reasoning-effort" to "high",
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

    @Test
    fun `completion - image`() {
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
    fun `completion with tool call`() {
        val meta = ToolMetadata(
            name = "date_tool_now",
            description = "Get the current date and time for a given location",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "The location to get the date and time for (e.g. 'Yaounde', 'Cameroon'). If not provided, ignore this parameter",
                    required = true
                )
            )
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        val config = mapOf(
            "model" to "kimi-k2.6",
            "tools" to listOf("date_tool_now"),
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(prompt = "What time is it at Paris?"),
            listOf(tool)
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.TOOL_CALLS, choices[0].finishReason)
        assertEquals(1, choices[0].toolCalls.size)
        assertEquals(meta.name, choices[0].toolCalls[0].name)
        assertEquals(mapOf("location" to "Paris"), choices[0].toolCalls[0].arguments)
    }

    @Test
    fun `completion with streaming AND tool call`() {
        val meta = ToolMetadata(
            name = "date_tool_now",
            description = "Get the current date and time for a given location",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "The location to get the date and time for (e.g. 'Yaounde', 'Cameroon'). If not provided, ignore this parameter",
                    required = false
                )
            )
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        val config = mapOf(
            "model" to "kimi-k2.6",
            "streaming" to true,
            "tools" to listOf("date_tool_now"),
        )
        llm.init(config, context)

        val response = llm.completionStream(
            request = LLMRequest(prompt = "What time is it at Paris?"),
            listOf(tool),
            onChunk = { chunk ->
                println("Chunk: " + chunk.delta)
            }
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.TOOL_CALLS, choices[0].finishReason)
        assertEquals(1, choices[0].toolCalls.size)
        assertEquals(meta.name, choices[0].toolCalls[0].name)
        assertEquals(mapOf("location" to "Paris"), choices[0].toolCalls[0].arguments)
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

    @Test
    fun availableModels() {
        llm.init(config, context)

        assertEquals(
            listOf(
                "kimi-k3",
                "kimi-k2.7-code",
                "kimi-k2.7-code-highspeed",
                "kimi-k2.6",
                "kimi-k2.5",
                "moonshot-v1-128k",
                "moonshot-v1-32k",
                "moonshot-v1-8k",
                "moonshot-v1-128k-vision-preview",
                "moonshot-v1-32k-vision-preview",
                "moonshot-v1-8k-vision-preview"
            ),
            llm.availableModels()
        )
    }

    @Test
    fun getMaxContextWindow() {
        val models = listOf(
            "kimi-k3",
            "kimi-k2.6",
            "kimi-k2.7-code",
            "kimi-k2.7-code-highspeed",
            "kimi-k2.5",
            "moonshot-v1-128k",
            "moonshot-v1-32k",
            "moonshot-v1-8k",
            "moonshot-v1-128k-vision-preview",
            "moonshot-v1-32k-vision-preview",
            "moonshot-v1-8k-vision-preview"
        )
        val expected = listOf(
            1024 * 1024,
            256 * 1024,
            256 * 1024,
            256 * 1024,
            256 * 1024,
            128 * 1024,
            32 * 1024,
            8 * 1024,
            128 * 1024,
            32 * 1024,
            8 * 1024
        )

        models.forEach { model ->
            llm.init(mapOf("model" to model), context)
            assertEquals(expected[models.indexOf(model)], llm.getMaxContextWindow(), model)
        }
    }
}
