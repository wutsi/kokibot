package com.wutsi.kokibot.channel.websocket

data class WebSocketRequest(
    val query: String,
    val userId: String? = null,
    val filePaths: List<String> = emptyList(),
)
