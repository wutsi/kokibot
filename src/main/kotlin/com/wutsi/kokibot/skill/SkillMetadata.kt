package com.wutsi.kokibot.skill

import java.io.File

data class SkillMetadata(
    val name: String,
    val home: File,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val requiredBinaries: List<String> = emptyList(),
    val requiredEnv: List<String> = emptyList(),
    val requiredSetup: List<String> = emptyList(),
    val requiredOS: List<String> = emptyList()
)
