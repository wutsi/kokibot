package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
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
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepseekTest {
    private val llm = Deepseek()
    private val credentialService = mock<CredentialService>()
    private val config = mapOf(
        "model" to "deepseek-v4-flash",
        "thinking" to false,
        "max-tokens" to 2024,
        "temperature" to 0.7,
        "read-timeout-millis" to 30000,
        "connect-timeout-millis" to 10000,
        "tools" to listOf("date_tool_now", "web_tool_search", "web_tool_fetch"),
    )
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        config = config,
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.deepseek")).doReturn(System.getenv("DEEPSEEK_API_KEY") ?: "")
    }

    @Test
    fun id() {
        assertEquals("llm:deepseek", llm.id())
    }

    @Test
    fun init() {
        llm.init(config, context)

        assertEquals("deepseek-v4-flash", llm.model)
        assertEquals(false, llm.thinking)
        assertEquals(2024, llm.maxTokens)
        assertEquals(.7, llm.temperature)
        assertEquals(30000, llm.readTimeoutMillis)
        assertEquals(10000, llm.connectTimeoutMillis)

        doReturn(System.getenv("DEEPSEEK_API_KEY")).whenever(credentialService).get("llm.deepseek")
    }

    @Test
    fun `init - no model`() {
        val config = mapOf<String, Any>()
        assertThrows<ConfigurationException> { llm.init(config, context) }
    }

    @Test
    fun balance() {
        val config = mapOf(
            "model" to "deepseek-v4-flash",
        )
        llm.init(config, context)

        val response = llm.balance()
        println(response)

        assertNotNull(response)
        assertTrue(response.total >= 0.0)
        assertEquals("USD", response.currency)
    }

    @Test
    fun completion() {
        val config = mapOf(
            "model" to "deepseek-v4-flash",
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
    fun `completion with tool call`() {
        val meta = ToolMetadata(
            name = "date_tool_now",
            description = "Get the current date and time at Paris",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "The location to get the date and time for (e.g. 'Paris', 'Cameroon'). If not provided, ignore this parameter",
                    required = false
                )
            )
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        val config = mapOf(
            "model" to "deepseek-v4-flash",
            "tools" to listOf("date_tool_now"),
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(prompt = "What time is it?"),
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
    fun `completion with PDF file`() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you summarize this text?",
                files = listOf(
                    File(this::class.java.getResource("/file/RL-1.pdf")!!.file)
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

    //    @Test
    fun `completion with image`() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you describe this image?",
                files = listOf(
                    File(this::class.java.getResource("/file/medic.png")!!.file)
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
    fun `completion with streaming`() {
        val config = mapOf(
            "model" to "deepseek-v4-flash",
            "streaming" to true,
        )
        llm.init(config, context)

        val response = llm.completionStream(
            request = LLMRequest(prompt = "What is the capital of France?"),
            emptyList(),
            onChunk = { chunk ->
                if (chunk.delta != null) {
                    println("Chunk: " + chunk.delta)
                }
            }
        )
        // println(response)

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(true, choices[0].content?.contains("Paris"))
        assertEquals(true, choices[0].toolCalls.isEmpty())
    }

    @Test
    fun `completion with streaming AND tool call`() {
        val meta = ToolMetadata(
            name = "date_tool_now",
            description = "Get the current date and time at Paris",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "The location to get the date and time for (e.g. 'Paris', 'Cameroon'). If not provided, ignore this parameter",
                    required = false
                )
            )
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        val config = mapOf(
            "model" to "deepseek-v4-flash",
            "streaming" to true,
            "tools" to listOf("date_tool_now"),
        )
        llm.init(config, context)

        val response = llm.completionStream(
            request = LLMRequest(prompt = "What time is it?"),
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
    fun `health - up`() {
        val config = mapOf(
            "model" to "deepseek-v4-flash",
        )
        llm.init(config, context)

        val health = llm.health()

        assertEquals(llm.id(), health.id)
        assertEquals(true, health.up)
        assertNull(health.details)
    }

    @Test
    fun `health - down`() {
        whenever(credentialService.get("llm.deepseek")).doReturn("xxxxx")
        val config = mapOf(
            "model" to "deepseek-v4-flash",
        )
        llm.init(config, context)

        val health = llm.health()

        assertEquals(llm.id(), health.id)
        assertEquals(false, health.up)
        assertNotNull(health.details)
    }

    @Test
    fun getMaxContextWindow() {
        assertEquals(1024 * 1024, llm.getMaxContextWindow())
    }

    @Test
    fun availableModels() {
        llm.init(config, context)

        assertEquals(
            listOf(
                "deepseek-v4-flash",
                "deepseek-v4-pro",
            ),
            llm.availableModels()
        )
    }

    @Test
    fun `apply - reasoning-effort`() {
        llm.init(config, context)

        llm.apply("reasoning-effort", "high")

        assertEquals("high", llm.reasoningEffort)
    }

    @Test
    fun `apply - temperature`() {
        llm.init(config, context)

        llm.apply("temperature", "0.5")

        assertEquals(0.5, llm.temperature)
    }

    @Test
    fun `apply - unknown setting`() {
        llm.init(config, context)

        assertThrows<ConfigurationException> { llm.apply("unknown-setting", "value") }
    }
}
