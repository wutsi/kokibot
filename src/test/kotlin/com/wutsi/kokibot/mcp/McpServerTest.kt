package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpServerTest {
    private val transport = mock<McpHttpTransport>()
    private val toolRegistry = mock<ToolRegistry>()

    private val config = McpServerConfig(
        name = "weather-mcp",
        description = "Weather data",
        url = "https://weather.example.com/mcp",
        token = "tok-123",
    )

    private val initResponse = McpHttpResponse(
        statusCode = 200,
        headers = mapOf("Mcp-Session-Id" to "sess-1"),
        body = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"weather","version":"1.0"}}}""",
    )

    private val listToolsResponse = McpHttpResponse(
        statusCode = 200,
        headers = emptyMap(),
        body = """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_weather","description":"Get weather"},{"name":"get_forecast","description":"Get forecast"}]}}""",
    )

    private val server = McpServer(config, transport)

    @BeforeEach
    fun setUp() {
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
    }

    @Test
    fun `id returns mcp-prefixed name`() {
        assertEquals("mcp:weather-mcp", server.id())
    }

    @Test
    fun `health returns up`() {
        assertTrue(server.health().up)
    }

    @Test
    fun `activate connects to server and registers tools`() {
        assertFalse(server.activated)

        server.activate(toolRegistry)

        assertTrue(server.activated)
        verify(toolRegistry, times(2)).register(any())
    }

    @Test
    fun `activate second call is no-op`() {
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
        server.activate(toolRegistry)

        // Second call — must NOT re-register tools
        server.activate(toolRegistry)

        verify(toolRegistry, times(2)).register(any()) // still only 2 from first activation
    }
}
