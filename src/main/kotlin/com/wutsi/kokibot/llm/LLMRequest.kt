package com.wutsi.kokibot.llm

import java.io.File

data class LLMRequest(
    val prompt: String,
    val systemInstructions: String? = null,
    val files: List<File> = emptyList(),
)
