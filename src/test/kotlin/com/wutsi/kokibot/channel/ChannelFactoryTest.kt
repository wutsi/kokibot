package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class ChannelFactoryTest {
    val agent = mock<Assistant>()
    val factory = ChannelFactory()

    @Test
    fun telegram() {
        val channel = factory.create("telegram", agent)
        assertTrue(channel is TelegramChannel)
    }

    @Test
    fun unsupported() {
        assertThrows<ConfigurationException> {
            factory.create("unknown", agent)
        }
    }
}
