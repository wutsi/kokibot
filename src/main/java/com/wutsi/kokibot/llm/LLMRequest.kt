package com.wutsi.kokibot.llm

data class LLMRequest(
    val prompt: String,
    val systemInstructions: String? = null,
)
