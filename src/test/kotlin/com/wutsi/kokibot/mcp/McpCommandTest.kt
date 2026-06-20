package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpCommandTest {
    private val mcpRegistry = mock<McpRegistry>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        mcpRegistry = mcpRegistry,
    )
    private val cmd = McpCommand()

    @Test
    fun metadata() {
        assertEquals(McpCommand.NAME, cmd.metadata().name)
    }

    @Test
    fun `exec list - shows all servers`() {
        val server1 = mock<McpServer>()
        val server2 = mock<McpServer>()
        doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server1).config
        doReturn(McpServerConfig(name = "news-mcp", description = "News feeds", url = "https://n.example.com")).whenever(server2).config
        doReturn(listOf(server1, server2)).whenever(mcpRegistry).all()
        // server2 is activated
        context.activatedMcps.add(server2)

        val result = cmd.exec(Message(text = ""), context)

        assertTrue(result.contains("2 MCP server(s)"))
        assertTrue(result.contains("weather-mcp: Weather data [not activated]"))
        assertTrue(result.contains("news-mcp: News feeds [activated]"))
    }

    @Test
    fun `exec list - empty registry`() {
        doReturn(emptyList<McpServer>()).whenever(mcpRegistry).all()

        val result = cmd.exec(Message(text = ""), context)

        assertTrue(result.contains("0 MCP server(s)"))
    }

    @Test
    fun `exec with name shows server details`() {
        val server = mock<McpServer>()
        doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server).config
        doReturn(server).whenever(mcpRegistry).get("weather-mcp")

        val result = cmd.exec(Message(text = "weather-mcp"), context)

        assertTrue(result.contains("weather-mcp"))
        assertTrue(result.contains("Weather data"))
        assertTrue(result.contains("https://w.example.com"))
    }

    @Test
    fun `exec with unknown name returns not found`() {
        doThrow(McpNotFoundException("MCP server not found: unknown")).whenever(mcpRegistry).get("unknown")

        val result = cmd.exec(Message(text = "unknown"), context)

        assertTrue(result.contains("not found"))
        assertTrue(result.contains("unknown"))
    }
}
