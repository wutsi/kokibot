package com.wutsi.kokibot.channel.websocket

data class WebSocketRequest(
    val query: String,
    val filePaths: List<String> = emptyList(),
)
