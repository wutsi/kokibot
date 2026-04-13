package com.wutsi.kokibot

data class Health(
    val id: String,
    val up: Boolean = true,
    val details: String? = null,
    val children: List<Health> = emptyList(),
)
