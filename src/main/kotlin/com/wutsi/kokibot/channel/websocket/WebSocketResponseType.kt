package com.wutsi.kokibot.channel.websocket

enum class WebSocketResponseType {
    QUEUED,
    REASONING_CHUNK,
    TOOL_STATUS,
    FINAL,
    ERROR,
}
