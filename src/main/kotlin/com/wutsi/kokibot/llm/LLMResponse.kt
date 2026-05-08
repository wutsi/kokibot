package com.wutsi.kokibot.llm

data class LLMResponse(
    val id: String = "",
    val choices: List<LLMResponseChoice>,
    val model: String? = null,
    val usage: LLMUsage? = null,
)
