package com.wutsi.kokibot.llm

data class LLMResponseChoice(
    val index: Int = -1,
    val finishReason: LLMFinishReason? = null,
    val content: String,
    val reasoningContent: String?,
    val toolCalls: List<LLMToolCall> = emptyList(),
)
