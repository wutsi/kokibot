package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpClientTest {
    private val transport = mock<McpHttpTransport>()
    private val client = McpClient(
        url = "https://mcp.example.com/api",
        token = "my-token",
        transport = transport,
    )

    private val initResponse = McpHttpResponse(
        statusCode = 200,
        headers = mapOf("Mcp-Session-Id" to "session-abc"),
        body = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"test","version":"1.0"}}}""",
    )

    private val listToolsResponse = McpHttpResponse(
        statusCode = 200,
        headers = mapOf("Mcp-Session-Id" to "session-abc"),
        body = """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_weather","description":"Get weather for a city","inputSchema":{"type":"object","properties":{"city":{"type":"string","description":"City name"}},"required":["city"]}}]}}""",
    )

    private val callToolResponse = McpHttpResponse(
        statusCode = 200,
        headers = emptyMap(),
        body = """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Sunny, 72F"}],"isError":false}}""",
    )

    @BeforeEach
    fun setUp() {
        doReturn(initResponse).whenever(transport).post(any(), any(), any())
    }

    @Test
    fun `initialize sends correct JSON-RPC payload and stores session ID`() {
        val headersCaptor = argumentCaptor<Map<String, String>>()
        val bodyCaptor = argumentCaptor<String>()

        client.initialize()

        verify(transport).post(eq("https://mcp.example.com/api"), headersCaptor.capture(), bodyCaptor.capture())

        val headers = headersCaptor.firstValue
        assertEquals("Bearer my-token", headers["Authorization"])
        assertEquals("application/json", headers["Content-Type"])

        val body = bodyCaptor.firstValue
        assertTrue(body.contains("\"method\":\"initialize\""))
        assertTrue(body.contains("\"protocolVersion\":\"2024-11-05\""))
        assertTrue(body.contains("\"name\":\"kokibot\""))
    }

    @Test
    fun `initialize stores session ID from response header`() {
        client.initialize()

        // Verify subsequent calls include Mcp-Session-Id
        doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
        client.listTools()

        val headersCaptor = argumentCaptor<Map<String, String>>()
        verify(transport, times(2)).post(any(), headersCaptor.capture(), any())
        assertEquals("session-abc", headersCaptor.secondValue["Mcp-Session-Id"])
    }

    @Test
    fun `listTools returns parsed tool definitions`() {
        client.initialize()
        doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())

        val tools = client.listTools()

        assertEquals(1, tools.size)
        assertEquals("get_weather", tools[0].name)
        assertEquals("Get weather for a city", tools[0].description)
    }

    @Test
    fun `listTools sends correct JSON-RPC method`() {
        client.initialize()
        val bodyCaptor = argumentCaptor<String>()
        doReturn(listToolsResponse).whenever(transport).post(any(), any(), bodyCaptor.capture())

        client.listTools()

        assertTrue(bodyCaptor.firstValue.contains("\"method\":\"tools/list\""))
    }

    @Test
    fun `callTool sends correct payload and extracts text content`() {
        client.initialize()
        val bodyCaptor = argumentCaptor<String>()
        doReturn(callToolResponse).whenever(transport).post(any(), any(), bodyCaptor.capture())

        val result = client.callTool("get_weather", mapOf("city" to "Seattle"))

        assertEquals("Sunny, 72F", result)
        assertTrue(bodyCaptor.firstValue.contains("\"method\":\"tools/call\""))
        assertTrue(bodyCaptor.firstValue.contains("\"name\":\"get_weather\""))
        assertTrue(bodyCaptor.firstValue.contains("\"city\":\"Seattle\""))
    }

    @Test
    fun `callTool reinitializes on 404 and retries`() {
        client.initialize()
        val expiredResponse = McpHttpResponse(statusCode = 404, headers = emptyMap(), body = "{}")
        doReturn(expiredResponse).doReturn(initResponse).doReturn(callToolResponse)
            .whenever(transport).post(any(), any(), any())

        val result = client.callTool("get_weather", mapOf("city" to "Seattle"))

        assertEquals("Sunny, 72F", result)
        verify(transport, times(4)).post(any(), any(), any()) // init + 404 + reinit + retry
    }

    @Test
    fun `callTool without token omits Authorization header`() {
        val noTokenClient = McpClient(url = "https://mcp.example.com/api", transport = transport)
        doReturn(initResponse).whenever(transport).post(any(), any(), any())
        noTokenClient.initialize()

        val headersCaptor = argumentCaptor<Map<String, String>>()
        doReturn(callToolResponse).whenever(transport).post(any(), headersCaptor.capture(), any())
        noTokenClient.callTool("get_weather", mapOf("city" to "Seattle"))

        assertTrue("Authorization" !in headersCaptor.firstValue)
    }
}
