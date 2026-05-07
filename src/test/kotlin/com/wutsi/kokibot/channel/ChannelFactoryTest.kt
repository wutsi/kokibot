package com.wutsi.kokibot.channel

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.channel.email.EmailChannel
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChannelFactoryTest {
    val factory = ChannelFactory()

    @Test
    fun telegram() {
        val channel = factory.create("telegram")
        assertTrue(channel is TelegramChannel)
    }

    @Test
    fun email() {
        val channel = factory.create("email")
        assertTrue(channel is EmailChannel)
    }

    @Test
    fun unsupported() {
        assertThrows<ConfigurationException> {
            factory.create("unknown")
        }
    }
}
