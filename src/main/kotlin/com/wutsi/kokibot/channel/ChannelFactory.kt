package com.wutsi.kokibot.channel

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.channel.email.EmailChannel
import com.wutsi.kokibot.channel.telegram.TelegramChannel

class ChannelFactory {
    fun create(type: String): Channel {
        return when (type) {
            "telegram" -> TelegramChannel()
            "email" -> EmailChannel()
            else -> throw ConfigurationException("Unsupported channel type: $type")
        }
    }
}
