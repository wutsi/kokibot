package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMToolCall

/**
 * Result of a tool execution, used for parallel execution tracking
 */
data class ToolExecutionResult(
    val call: LLMToolCall,
    val result: String,
    val error: Exception? = null,
)
