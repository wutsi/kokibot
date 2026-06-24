package com.wutsi.kokibot.dto

data class AssistantDto(
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val coordinator: Boolean = false,
    val instructions: String = "",
)
