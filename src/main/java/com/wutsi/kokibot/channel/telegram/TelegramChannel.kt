package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.exception.ConfigurationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient

class TelegramChannel(
    agent: Assistant,
    val factory: TelegramFactory = TelegramFactory(),
) : Channel(agent), LongPollingSingleThreadUpdateConsumer {
    companion object {
        const val TYPING_DELAY = 2000L
    }

    private lateinit var app: TelegramBotsLongPollingApplication
    private lateinit var client: TelegramClient
    private var botToken: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("telegram-channel"))

    override fun init(config: Map<*, *>) {
        this.botToken = config["token"]?.toString()
            ?: throw ConfigurationException("token is required")

        app = factory.createTelegramBotsLongPollingApplication()
        app.registerBot(botToken, this)
        client = factory.createTelegramClient(botToken!!)
    }

    override fun destroy() {
        botToken?.let { token ->
            app.unregisterBot(token)
        }
    }

    override fun consume(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val chatId = update.message.chatId.toString()

            /* Typing indicator */
            val job = scope.launch {
                while (true) {
                    ensureActive()
                    typing(chatId)
                    delay(TYPING_DELAY)
                }
            }

            /* Process message */
            val message = try {
                agent.process(
                    Message(
                        text = update.message.text,
                        role = Role.USER,
                    )
                )
            } finally {
                job.cancel()
            }

            /* Send response */
            send(chatId, message.text)
        }
    }

    private fun typing(chatId: String) {
        val action = SendChatAction.builder()
            .chatId(chatId)
            .action(ActionType.TYPING.toString())
            .build()
        client.execute(action)
    }

    private fun send(chatId: String, text: String) {
        val sendMessage = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .parseMode(ParseMode.MARKDOWN)
            .build()
        client.execute(sendMessage)
    }
}
