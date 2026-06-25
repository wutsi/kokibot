package com.wutsi.kokibot.service.kb

import java.time.LocalDateTime

data class KBEntry(
    val name: String = "",
    val scope: String = "",
    val keywords: List<String> = emptyList(),
    val summary: String? = null,
    val source: String = "",
    val raw: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val contentType: String = "",
)
