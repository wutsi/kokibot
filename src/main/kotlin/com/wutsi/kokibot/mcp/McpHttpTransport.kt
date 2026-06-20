package com.wutsi.kokibot.mcp

interface McpHttpTransport {
    fun post(url: String, headers: Map<String, String>, body: String): McpHttpResponse
}
