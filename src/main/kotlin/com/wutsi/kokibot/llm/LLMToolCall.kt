package com.wutsi.kokibot.llm

import java.util.UUID

data class LLMToolCall(
    val name: String,
    val arguments: Map<*, *> = emptyMap<String, Any>(),
    val id: String = UUID.randomUUID().toString(),
)
