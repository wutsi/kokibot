package com.wutsi.kokibot.mcp

import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.atomic.AtomicInteger

class McpClient(
    private val url: String,
    private val token: String? = null,
    private val transport: McpHttpTransport = McpOkHttpTransport(),
    private val jsonMapper: JsonMapper = JsonMapper(),
) {
    private var sessionId: String? = null
    private val requestId = AtomicInteger(0)

    fun initialize() {
        val payload = buildPayload(
            "initialize",
            mapOf(
                "protocolVersion" to "2024-11-05",
                "capabilities" to emptyMap<String, Any>(),
                "clientInfo" to mapOf("name" to "kokibot", "version" to "1.0"),
            ),
        )
        val response = post(payload)
        sessionId = response.headers["Mcp-Session-Id"] ?: response.headers["mcp-session-id"]
    }

    fun listTools(): List<McpToolDefinition> {
        val response = post(buildPayload("tools/list"))
        val body = parseBody(response.body)
        val result = body["result"] as? Map<*, *> ?: return emptyList()
        val tools = result["tools"] as? List<*> ?: return emptyList()
        return tools.filterIsInstance<Map<*, *>>().map { tool ->
            McpToolDefinition(
                name = tool["name"]?.toString() ?: "",
                description = tool["description"]?.toString(),
                inputSchema = (tool["inputSchema"] as? Map<*, *>)
                    ?.entries?.associate { it.key.toString() to (it.value ?: "") } ?: emptyMap(),
            )
        }
    }

    fun callTool(name: String, arguments: Map<*, *>): String {
        val payload = buildPayload(
            "tools/call",
            mapOf("name" to name, "arguments" to arguments),
        )
        val response = post(payload)
        if (response.statusCode == 404 || response.statusCode == 400) {
            initialize()
            val retry = post(payload)
            return extractContent(retry.body)
        }
        return extractContent(response.body)
    }

    private fun post(payload: Map<String, Any>): McpHttpResponse {
        val headers = mutableMapOf("Content-Type" to "application/json")
        token?.let { headers["Authorization"] = "Bearer $it" }
        sessionId?.let { headers["Mcp-Session-Id"] = it }
        return transport.post(url, headers, jsonMapper.writeValueAsString(payload))
    }

    private fun buildPayload(method: String, params: Map<*, *>? = null): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "jsonrpc" to "2.0",
            "id" to requestId.incrementAndGet(),
            "method" to method,
        )
        params?.let { payload["params"] = it }
        return payload
    }

    private fun parseBody(body: String): Map<*, *> =
        jsonMapper.readValue(body, Map::class.java)

    private fun extractContent(body: String): String {
        val parsed = parseBody(body)
        val result = parsed["result"] as? Map<*, *> ?: return body
        val content = result["content"] as? List<*> ?: return body
        return content.filterIsInstance<Map<*, *>>()
            .filter { it["type"] == "text" }
            .mapNotNull { it["text"]?.toString() }
            .joinToString("\n")
            .ifEmpty { body }
    }
}
