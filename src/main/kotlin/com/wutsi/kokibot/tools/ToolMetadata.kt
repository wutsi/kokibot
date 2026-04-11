package com.wutsi.kokibot.tools

data class ToolMetadata(
    val name: String,
    val description: String = "",
    val parameters: List<ToolParameter> = emptyList(),
)
