package com.wutsi.kokibot.service.memory

import java.time.LocalDateTime

data class Conversation(
    val id: String = "",
    val channelId: String = "",
    val title: String = "",
    val startDate: LocalDateTime = LocalDateTime.now(),
)

data class ConversationMessage(
    val role: String = "",
    val text: String = "",
    val files: List<String> = emptyList(),
    val dateTime: LocalDateTime = LocalDateTime.now(),
)

data class ConversationDetail(
    val id: String = "",
    val title: String = "",
    val startDate: LocalDateTime = LocalDateTime.now(),
    val messages: List<ConversationMessage> = emptyList(),
)
