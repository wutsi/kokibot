package com.wutsi.kokibot.channel.telegram

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient

class TelegramFactoryTest {
    private val factory: TelegramFactory = TelegramFactory()

    @Test
    fun createTelegramBotsLongPollingApplication() {
        val app = factory.createTelegramBotsLongPollingApplication()
        assertNotNull(app)
    }

    @Test
    fun createTelegramClient() {
        val client = factory.createTelegramClient("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11")
        assertTrue(client is OkHttpTelegramClient)
    }
}
