package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class DeepseekTest {
    val deepseek = Deepseek()
    private val toolRegistry = ToolRegistry()
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

    @Test
    fun init() {
        deepseek.init(config, toolRegistry)

        assertEquals("ds-000001", deepseek.client.apiKey)
        assertEquals("deepseek-chat", deepseek.client.model)
        assertEquals(true, deepseek.client.thinking)
        assertEquals(1000, deepseek.client.maxTokens)
        assertEquals(.7, deepseek.client.temperature)
        assertEquals(30000, deepseek.client.readTimeoutMillis)
        assertEquals(10000, deepseek.client.connectTimeoutMillis)
    }

    @Test
    fun `init - no api key`() {
        val config = mapOf(
            "model" to "deepseek-chat",
        )
        assertThrows<ConfigurationException> { deepseek.init(config, toolRegistry) }
    }

    @Test
    fun `init - no model`() {
        val config = mapOf(
            "api_key" to "ds-000001",
        )
        assertThrows<ConfigurationException> { deepseek.init(config, toolRegistry) }
    }

    @Test
    fun completion() {
        val config = mapOf(
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
        )
        deepseek.init(config, toolRegistry)

        val response = deepseek.completion(
            request = LLMRequest(prompt = "What is the capital of France?")
        )
        System.out.println(response)

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
        toolRegistry.register(tool)

        val config = mapOf(
            "api_key" to System.getenv("DEEPSEEK_API_KEY"),
            "model" to "deepseek-chat",
            "tools" to listOf("date_tool_now"),
        )
        deepseek.init(config, toolRegistry)

        val response = deepseek.completion(
            request = LLMRequest(prompt = "What time is it?")
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.TOOL_CALLS, choices[0].finishReason)
        assertEquals(1, choices[0].toolCalls.size)
        assertEquals(meta.name, choices[0].toolCalls[0].name)
        assertEquals(mapOf("location" to "Paris"), choices[0].toolCalls[0].arguments)
    }
}
