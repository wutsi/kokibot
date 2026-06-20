package com.wutsi.kokibot.mcp

data class McpToolDefinition(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any> = emptyMap(),
)
