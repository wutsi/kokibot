package com.wutsi.kokibot.mcp

data class McpServerConfig(
    val name: String,
    val description: String = "",
    val url: String,
    val token: String? = null,
)
