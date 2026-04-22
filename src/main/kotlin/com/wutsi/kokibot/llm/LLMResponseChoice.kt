package com.wutsi.kokibot.llm

data class LLMResponseChoice(
    val index: Int = -1,
    val finishReason: LLMFinishReason? = null,
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<LLMToolCall> = emptyList(),
)
