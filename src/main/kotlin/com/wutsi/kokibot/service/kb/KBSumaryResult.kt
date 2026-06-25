package com.wutsi.kokibot.service.kb

data class KBSumaryResult(
    val scope: String = "",
    val keywords: List<String> = emptyList(),
    val summary: String = "",
)
