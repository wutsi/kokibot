package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpActivationToolTest {
    private val mcpRegistry = mock<McpRegistry>()
    private val toolRegistry = mock<ToolRegistry>()
    private val server = mock<McpServer>()
    private val context = mock<Context>()

    private val tool = McpActivationTool()

    private val weatherTool = McpTool(
        serverName = "weather-mcp",
        toolDef = McpToolDefinition(name = "get_weather", description = "Get weather"),
        server = server,
    )
    private val forecastTool = McpTool(
        serverName = "weather-mcp",
        toolDef = McpToolDefinition(name = "get_forecast", description = "Get forecast"),
        server = server,
    )

    @BeforeEach
    fun setUp() {
        doReturn(mcpRegistry).whenever(context).mcpRegistry
        doReturn(toolRegistry).whenever(context).toolRegistry
        doReturn(server).whenever(mcpRegistry).get("weather-mcp")
        doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server).config
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(McpActivationTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)
        assertEquals("server", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `exec activates server and returns tool list`() {
        doReturn(true).whenever(server).activated
        doReturn(listOf(weatherTool, forecastTool)).whenever(toolRegistry).all()

        val result = tool.exec(mapOf("server" to "weather-mcp"))

        verify(server).activate(toolRegistry)
        assertTrue(result.contains("weather-mcp"))
        assertTrue(result.contains("Activated"))
    }

    @Test
    fun `exec returns error string when server not found`() {
        doThrow(McpNotFoundException("MCP server not found: unknown")).whenever(mcpRegistry).get("unknown")

        val result = tool.exec(mapOf("server" to "unknown"))

        assertTrue(result.contains("Unable to activate"))
        assertTrue(result.contains("unknown"))
    }

    @Test
    fun `exec returns error string when activation fails`() {
        doThrow(RuntimeException("Connection refused")).whenever(server).activate(any())

        val result = tool.exec(mapOf("server" to "weather-mcp"))

        assertTrue(result.contains("Unable to activate"))
        assertTrue(result.contains("Connection refused"))
    }

    @Test
    fun `exec throws when server parameter is missing`() {
        try {
            tool.exec(emptyMap<String, Any>())
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `exec throws when server parameter is empty`() {
        try {
            tool.exec(mapOf("server" to ""))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `statusText returns activation message`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = McpActivationTool.NAME, arguments = mapOf("server" to "weather-mcp"))
        )
        val result = tool.statusText(toolCalls)
        assertTrue(result.contains("weather-mcp"))
    }

    @Test
    fun `activate returns true`() {
        assertTrue(tool.activate())
    }
}
