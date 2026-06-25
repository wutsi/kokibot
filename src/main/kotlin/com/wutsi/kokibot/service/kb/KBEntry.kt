package com.wutsi.kokibot.service.kb

import java.time.LocalDateTime

data class KBEntry(
    val name: String = "",
    val scope: String = "",
    val keywords: List<String> = emptyList(),
    val summary: String = "",
    val source: String = "",
    val raw: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
