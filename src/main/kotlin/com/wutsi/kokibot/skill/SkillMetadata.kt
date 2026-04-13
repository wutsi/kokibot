package com.wutsi.kokibot.skill

import com.wutsi.kokibot.tools.ToolMetadata

data class SkillMetadata(
    val name: String,
    val description: String = "",
    val tools: List<ToolMetadata> = emptyList(),
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val requiredBins: List<String> = emptyList(),
    val requiredEnv: List<String> = emptyList(),
)
