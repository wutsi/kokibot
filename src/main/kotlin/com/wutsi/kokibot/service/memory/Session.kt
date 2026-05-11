package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Role
import com.wutsi.kokibot.llm.LLMUsage
import java.time.LocalDateTime

data class Session(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val iteration: Int? = null,
    val role: Role = Role.UNKNOWN,
    val userId: String? = null,
    val channelId: String? = null,

    val content: List<SessionContent> = emptyList(),
    val model: String? = null,
    val usage: LLMUsage? = null,
    val memory: List<String>? = null,
)

data class SessionContent(
    val type: String = "",
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val arguments: Map<String, String>? = null,
)
