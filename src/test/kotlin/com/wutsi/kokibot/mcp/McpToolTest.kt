package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpToolTest {
    private val mockClient = mock<McpClient>()
    private val mockServer = mock<McpServer>()

    private val toolDef = McpToolDefinition(
        name = "get_weather",
        description = "Get current weather for a city",
        inputSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "city" to mapOf("type" to "string", "description" to "City name"),
                "units" to mapOf("type" to "string", "description" to "Temperature units"),
            ),
            "required" to listOf("city"),
        ),
    )

    private val tool = McpTool(
        serverName = "weather-mcp",
        toolDef = toolDef,
        server = mockServer,
    )

    @Test
    fun `metadata returns namespaced tool name`() {
        val meta = tool.metadata()
        assertEquals("weather_mcp__get_weather", meta.name)
    }

    @Test
    fun `metadata returns tool description`() {
        val meta = tool.metadata()
        assertEquals("Get current weather for a city", meta.description)
    }

    @Test
    fun `metadata maps inputSchema properties to parameters`() {
        val meta = tool.metadata()
        assertEquals(2, meta.parameters.size)

        val cityParam = meta.parameters.first { it.name == "city" }
        assertEquals(ToolParameterType.STRING, cityParam.type)
        assertEquals("City name", cityParam.description)
        assertTrue(cityParam.required)

        val unitsParam = meta.parameters.first { it.name == "units" }
        assertEquals(ToolParameterType.STRING, unitsParam.type)
        assertFalse(unitsParam.required)
    }

    @Test
    fun `activate returns false when server not activated`() {
        doReturn(false).whenever(mockServer).activated
        assertFalse(tool.activate())
    }

    @Test
    fun `activate returns true when server is activated`() {
        doReturn(true).whenever(mockServer).activated
        assertTrue(tool.activate())
    }

    @Test
    fun `exec delegates to server client callTool with original name`() {
        doReturn(mockClient).whenever(mockServer).client
        doReturn("Sunny, 72F").whenever(mockClient).callTool(any(), any())

        val result = tool.exec(mapOf("city" to "Seattle"))

        assertEquals("Sunny, 72F", result)
        verify(mockClient).callTool(eq("get_weather"), eq(mapOf("city" to "Seattle")))
    }

    @Test
    fun `statusText returns descriptive string`() {
        val toolCalls = listOf(LLMToolCall(name = "weather_mcp__get_weather", arguments = mapOf("city" to "Seattle")))
        val result = tool.statusText(toolCalls)
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("weather-mcp"))
    }

    @Test
    fun `id returns tool-prefixed namespaced name`() {
        assertEquals("tool:weather_mcp__get_weather", tool.id())
    }
}
