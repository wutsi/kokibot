package com.wutsi.kokibot.service.kb

import java.time.LocalDateTime

data class KBEntry(
    val name: String = "",
    val scope: String? = null,
    val keywords: List<String> = emptyList(),
    val summary: String? = null,
    val source: String? = null,
    val raw: String? = null,
    val url: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val error: String? = null,
    val type: KBEntryType = KBEntryType.UNKNOWN,
    val status: KBEntryStatus = KBEntryStatus.UNKNOWN,
)
