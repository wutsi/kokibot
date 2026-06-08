package com.wutsi.kokibot.channel.websocket

data class WebSocketResponse(
    val type: WebSocketResponseType,
    val content: String? = null,
    val message: String? = null,
    val finishReason: String? = null,
    val contextLength: Int? = null,
)
