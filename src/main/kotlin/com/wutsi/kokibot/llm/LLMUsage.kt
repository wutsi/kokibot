package com.wutsi.kokibot.llm

data class LLMUsage(
    val totalTokens: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val promptCacheHitTokens: Int? = null,
)
