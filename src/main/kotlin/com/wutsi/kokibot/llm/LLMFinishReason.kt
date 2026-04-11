package com.wutsi.kokibot.llm

enum class LLMFinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    INSUFFICIENT_SYSTEM_RESOURCE,
}
