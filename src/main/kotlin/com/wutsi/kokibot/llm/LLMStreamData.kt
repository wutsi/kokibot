package com.wutsi.kokibot.llm

/**
 * Data class representing streaming response data.
 * Contains both text delta and optional token usage information.
 *
 * @property text The text delta being streamed (can be empty string)
 * @property usage Optional token usage information (null if not available)
 */
data class LLMStreamData(
    val text: String = "",
    val usage: LLMUsage? = null,
)
