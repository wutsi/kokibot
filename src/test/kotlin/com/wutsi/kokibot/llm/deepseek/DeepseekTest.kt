package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class DeepseekTest {
    private val llm = Deepseek()
    private val config = mapOf(
        "api_key" to "ds-000001",
        "model" to "deepseek-chat",
        "thinking" to true,
        "max-tokens" to 1000,
        "temperature" to 0.7,
        "read-timeout-millis" to 30000,
        "connect-timeout-millis" to 10000,
        "tools" to listOf("date_tool_now", "web_tool_search", "web_tool_fetch"),
    )
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        config = config,
    )

    @Test
    fun id() {
        assertEquals("llm:deepseek", llm.id())
    }

    @Test
    fun init() {
        llm.init(config, context)

        assertEquals("ds-000001", llm.client.apiKey)
        assertEquals("deepseek-chat", llm.client.model)
        assertEquals(true, llm.client.thinking)
        assertEquals(1000, llm.client.maxTokens)
        assertEquals(.7, llm.client.temperature)
        assertEquals(30000, llm.client.readTimeoutMillis)
        assertEquals(10000, llm.client.connectTimeoutMillis)
    }

    @Test
    fun `init - no api key`() {
        val config = mapOf(
            "model" to "deepseek-chat",
        )
        assertThrows<ConfigurationException> { llm.init(config, context) }
    }

    @Test
    fun `init - no model`() {
        val config = mapOf(
            "api_key" to "ds-000001",
        )
        assertThrows<ConfigurationException> { llm.init(config, context) }
    }

    @Test
    fun completion() {
        val config = mapOf(
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
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
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
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
        val config = mapOf(
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you summarize thie text?",
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

    @Test
    fun `health - up`() {
        val config = mapOf(
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
        )
        llm.init(config, context)

        val health = llm.health()

        assertEquals(llm.id(), health.id)
        assertEquals(true, health.up)
        assertNull(health.details)
    }

    @Test
    fun `health - down`() {
        val config = mapOf(
            "api_key" to "xxxxx",
            "model" to "deepseek-chat",
        )
        llm.init(config, context)

        val health = llm.health()

        assertEquals(llm.id(), health.id)
        assertEquals(false, health.up)
        assertNotNull(health.details)
    }
}
