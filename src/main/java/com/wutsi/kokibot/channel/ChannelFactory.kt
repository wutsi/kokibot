package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import com.wutsi.kokibot.exception.ConfigurationException

class ChannelFactory {
    fun create(type: String, agent: Assistant): Channel {
        return when (type) {
            "telegram" -> TelegramChannel(agent)
            else -> throw ConfigurationException("Unsupported channel type: $type")
        }
    }
}
