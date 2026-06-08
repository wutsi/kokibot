package com.wutsi.kokibot.llm

data class LLMUsage(
    val totalTokens: Int = -1,
    val promptTokens: Int = -1,
    val completionTokens: Int = -1,
    val promptCacheHitTokens: Int? = null,
)
