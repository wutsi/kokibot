package com.wutsi.kokibot.tools.mcp

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.mcp.McpClient
import com.wutsi.kokibot.mcp.McpNotFoundException
import com.wutsi.kokibot.mcp.McpRegistry
import com.wutsi.kokibot.mcp.McpServer
import com.wutsi.kokibot.mcp.McpServerConfig
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpCallToolTest {
    private val mcpRegistry = mock<McpRegistry>()
    private val mcpClient = mock<McpClient>()
    private val server = mock<McpServer>()
    private val context = mock<Context>()
    private val activatedMcps: MutableList<McpServer> = CopyOnWriteArrayList()

    private val tool = McpCallTool()

    @BeforeEach
    fun setUp() {
        doReturn(mcpRegistry).whenever(context).mcpRegistry
        doReturn(activatedMcps).whenever(context).activatedMcps
        doReturn(server).whenever(mcpRegistry).get("weather-mcp")
        doReturn(mcpClient).whenever(server).client
        doReturn(
            McpServerConfig(
                name = "weather-mcp",
                description = "Weather data",
                url = "https://w.example.com"
            )
        ).whenever(server).config
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `metadata returns correct name and 3 parameters`() {
        val meta = tool.metadata()
        assertEquals(McpCallTool.NAME, meta.name)
        assertEquals(3, meta.parameters.size)
        assertEquals("server", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
        assertEquals("tool", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertTrue(meta.parameters[1].required)
        assertEquals("arguments", meta.parameters[2].name)
        assertEquals(ToolParameterType.OBJECT, meta.parameters[2].type)
        assertFalse(meta.parameters[2].required)
    }

    @Test
    fun `activate returns false when activatedMcps is empty`() {
        assertFalse(tool.activate())
    }

    @Test
    fun `activate returns true when activatedMcps has entries`() {
        activatedMcps.add(server)
        assertTrue(tool.activate())
    }

    @Test
    fun `exec calls callTool on the correct server`() {
        activatedMcps.add(server)
        doReturn("Sunny, 72F").whenever(mcpClient).callTool("get_weather", mapOf("city" to "Seattle"))

        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
                "arguments" to mapOf("city" to "Seattle"),
            )
        )

        verify(mcpClient).callTool("get_weather", mapOf("city" to "Seattle"))
        assertEquals("Sunny, 72F", result)
    }

    @Test
    fun `exec returns error when server not activated`() {
        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("not activated"))
        assertTrue(result.contains("mcp_activate"))
    }

    @Test
    fun `exec returns error when server not found`() {
        doThrow(McpNotFoundException("MCP server not found: unknown")).whenever(mcpRegistry).get("unknown")

        val result = tool.exec(
            mapOf(
                "server" to "unknown",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("Error calling tool"))
        assertTrue(result.contains("unknown"))
    }

    @Test
    fun `exec returns error when callTool throws`() {
        activatedMcps.add(server)
        doThrow(RuntimeException("Connection refused")).whenever(mcpClient)
            .callTool("get_weather", emptyMap<String, Any>())

        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("Error calling tool"))
        assertTrue(result.contains("Connection refused"))
    }

    @Test
    fun `exec throws IllegalArgumentException when server missing`() {
        try {
            tool.exec(mapOf("tool" to "get_weather"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `exec throws IllegalArgumentException when tool missing`() {
        try {
            tool.exec(mapOf("server" to "weather-mcp"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `statusText returns server and tool name`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "1",
                name = McpCallTool.NAME,
                arguments = mapOf("server" to "weather-mcp", "tool" to "get_weather")
            )
        )
        val result = tool.statusText(toolCalls)
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("weather-mcp"))
    }
}
