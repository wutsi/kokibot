package com.wutsi.kokibot.service.memory

import java.time.LocalDateTime

data class Conversation(
    val id: String,
    val channelId: String,
    val title: String,
    val startDate: LocalDateTime,
)

data class ConversationMessage(
    val role: String,
    val text: String,
    val dateTime: LocalDateTime,
)

data class ConversationDetail(
    val id: String,
    val title: String,
    val startDate: LocalDateTime,
    val messages: List<ConversationMessage>,
)
