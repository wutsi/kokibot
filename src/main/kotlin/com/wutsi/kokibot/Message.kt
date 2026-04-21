package com.wutsi.kokibot

import java.time.LocalDateTime
import java.util.UUID

data class Message(
    val text: String = "",
    val role: Role = Role.UNKNOWN,
    val finishReason: FinishReason = FinishReason.UNKNOWN,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val channelId: String? = null,
    val userId: String? = null,
    val filePaths: List<String> = emptyList(),
    val id: String = UUID.randomUUID().toString(),
)
