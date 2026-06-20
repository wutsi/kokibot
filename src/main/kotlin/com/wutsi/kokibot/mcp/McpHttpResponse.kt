package com.wutsi.kokibot.mcp

data class McpHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
)
