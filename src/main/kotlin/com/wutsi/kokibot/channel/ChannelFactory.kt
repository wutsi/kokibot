package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.channel.email.EmailChannel
import com.wutsi.kokibot.channel.telegram.TelegramChannel

class ChannelFactory {
    fun create(type: String, assistant: Assistant): Channel {
        return when (type) {
            "telegram" -> TelegramChannel(assistant)
            "email" -> EmailChannel(assistant)
            else -> throw ConfigurationException("Unsupported channel type: $type")
        }
    }
}
