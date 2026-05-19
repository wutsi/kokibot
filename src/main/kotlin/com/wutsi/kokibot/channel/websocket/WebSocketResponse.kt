package com.wutsi.kokibot.channel.websocket

data class WebSocketResponse(
    val type: WebSocketResponseType,
    val content: String? = null,
    val message: String? = null,
    val finishReason: String? = null,
)

enum class WebSocketResponseType {
    REASONING_CHUNK,
    FINAL,
    ERROR,
}
