package com.wutsi.kokibot.channel

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.channel.email.EmailChannel
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import com.wutsi.kokibot.channel.websocket.WebSocketChannel

class ChannelFactory {
    fun create(type: String): Channel {
        return when (type) {
            "telegram" -> TelegramChannel()
            "email" -> EmailChannel()
            "websocket" -> WebSocketChannel()
            else -> throw ConfigurationException("Unsupported channel type: $type")
        }
    }
}
