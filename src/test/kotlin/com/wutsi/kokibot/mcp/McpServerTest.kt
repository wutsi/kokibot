package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.service.credential.CredentialService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpServerTest {
    private val transport = mock<McpHttpTransport>()
    private val credentialService = mock<CredentialService>()
    private val context = Context(
        home = File("target/test-data/mcp"),
        llm = mock(),
        credentialService = credentialService,
    )

    private val configMap = mapOf(
        "name" to "weather-mcp",
        "description" to "Weather data",
        "url" to "https://weather.example.com/mcp",
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

    private val server = McpServer(transport)

    @BeforeEach
    fun setUp() {
        whenever(credentialService.getOrNull("mcp.weather-mcp")).doReturn("tok-123")
        server.init(configMap, context)
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
    fun `initialize sets toolDefinitions`() {
        server.initialize()

        assertTrue(server.toolDefinitions.isNotEmpty())
        assertEquals(2, server.toolDefinitions.size)
        assertEquals("get_weather", server.toolDefinitions[0].name)
        assertEquals("get_forecast", server.toolDefinitions[1].name)
    }

    @Test
    fun `initialize is no-op when called twice`() {
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
        server.initialize()

        // Second call — must NOT re-initialize
        server.initialize()

        // Only 2 posts from the first call (initialize + listTools)
        verify(transport, times(2)).post(any(), any(), any())
    }
}
