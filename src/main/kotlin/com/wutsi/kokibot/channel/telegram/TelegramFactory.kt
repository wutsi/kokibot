package com.wutsi.kokibot.channel.telegram

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.generics.TelegramClient

class TelegramFactory {
    fun createTelegramBotsLongPollingApplication(): TelegramBotsLongPollingApplication {
        return TelegramBotsLongPollingApplication()
    }

    fun createTelegramClient(botToken: String): TelegramClient {
        return OkHttpTelegramClient(botToken)
    }
}
