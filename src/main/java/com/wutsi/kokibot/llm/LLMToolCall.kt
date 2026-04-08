package com.wutsi.kokibot.llm

data class LLMToolCall(
    val name: String,
    val arguments: Map<*, *> = emptyMap<String, Any>(),
)
