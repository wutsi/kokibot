package com.wutsi.kokibot.tools

data class ToolParameter(
    val name: String,
    val type: ToolParameterType,
    val description: String,
    val required: Boolean = false,
)
