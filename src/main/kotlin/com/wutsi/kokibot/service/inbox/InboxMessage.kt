package com.wutsi.kokibot.service.inbox

import java.time.LocalDateTime

data class InboxMessage(
    val id: String = "",
    val channelId: String? = null,
    val userId: String? = null,
    val text: String = "",
    val filePaths: List<String> = emptyList(),
    val subject: String? = null,
    val conversationId: String? = null,
    val submittedAt: LocalDateTime = LocalDateTime.now(),
    val processedAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
    val response: String? = null,
    val error: String? = null,
)
