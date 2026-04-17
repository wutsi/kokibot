package com.wutsi.kokibot.skill

data class SkillMetadata(
    val name: String,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val requiredBinaries: List<String> = emptyList(),
    val requiredEnv: List<String> = emptyList(),
    val requiredSetup: List<String> = emptyList(),
)
