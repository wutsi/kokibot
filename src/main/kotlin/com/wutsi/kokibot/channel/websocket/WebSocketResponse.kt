package com.wutsi.kokibot.channel.websocket

import com.wutsi.kokibot.llm.LLMUsage

data class WebSocketResponse(
    val type: WebSocketResponseType,
    val content: String? = null,
    val message: String? = null,
    val finishReason: String? = null,
    val usage: LLMUsage? = null,
)
