package com.wutsi.kokibot

import java.time.LocalDateTime

data class Message(
    val text: String = "",
    val role: Role = Role.UNKNOWN,
    val finishReason: FinishReason = FinishReason.UNKNOWN,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val userId: String? = null,
    val filePaths: List<String> = emptyList(),
)
