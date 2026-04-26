package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.RestBuilder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Document
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File

class TelegramChannel(
    assistant: Assistant,
    val factory: TelegramFactory = TelegramFactory(),
    val restBuilder: RestBuilder = RestBuilder(),
) : Channel(assistant), LongPollingSingleThreadUpdateConsumer {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramChannel::class.java)

        const val ID = "channel:telegram"
        const val TYPING_DELAY = 2000L
        const val ERROR_UNSUPPORTED_MESSAGE = "Sorry, I can only process text messages and documents for now."
        const val ERROR_UNAUTHORIZED_MESSAGE = "Sorry, you are not authorized to interact with me."
    }

    private lateinit var app: TelegramBotsLongPollingApplication
    private lateinit var client: TelegramClient
    private lateinit var botToken: String
    private lateinit var context: Context
    private lateinit var rest: RestTemplate
    private lateinit var senderWhitelist: List<String>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("telegram-channel"))

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.botToken = config["token"]?.toString()
            ?: throw ConfigurationException("token is required")

        app = factory.createTelegramBotsLongPollingApplication()
        app.registerBot(botToken, this)
        client = factory.createTelegramClient(botToken)
        rest = restBuilder.build(null, null)
        senderWhitelist = MapUtil.toList("sender-whitelist", config)
            ?.mapNotNull { entry -> entry?.toString() }
            ?: emptyList()
        this.context = context
    }

    override fun destroy() {
        try {
            app.unregisterBot(botToken)
        } catch (e: Exception) {
            LOGGER.warn("error during telegram channel destroy", e)
        }
    }

    override fun health(): Health {
        try {
            val response = rest.getForEntity("https://api.telegram.org/bot$botToken/getMe", Map::class.java).body
            return Health(id(), response?.get("ok") == true)
        } catch (ex: Exception) {
            LOGGER.warn("error during telegram channel heath", ex)
            return Health(id(), false, ex.message)
        }
    }

    override fun consume(update: Update) {
        if (update.hasMessage()) {
            val chatId = update.message.chatId.toString()

            /* Check sender */
            if (!accept(update)) {
                send(
                    chatId,
                    Message(
                        text = ERROR_UNAUTHORIZED_MESSAGE,
                    ),
                    true,
                )
                return
            }

            /* Typing indicator */
            val job = scope.launch {
                while (true) {
                    ensureActive()
                    typing(chatId)
                    delay(TYPING_DELAY)
                }
            }

            /* Process message */
            val userId = update.message.chat.id.toString()
            val message = try {
                if (update.message.hasText()) {
                    assistant.process(
                        Message(
                            text = update.message.text,
                            role = Role.USER,
                            userId = userId,
                            channelId = id(),
                        )
                    )
                } else if (update.message.hasDocument()) {
                    val file = download(update.message.document)
                    assistant.process(
                        Message(
                            text = "File received: ${update.message.document.fileName}. Do not process this document, just return the message `File received`",
                            role = Role.USER,
                            userId = userId,
                            channelId = id(),
                            filePaths = listOf(file.absolutePath)
                        )
                    )
                } else {
                    Message(
                        text = ERROR_UNSUPPORTED_MESSAGE,
                        role = Role.SYSTEM,
                    )
                }
            } finally {
                job.cancel()
            }

            /* Send response */
            send(chatId, message, true)
        }
    }

    override fun send(message: Message): Boolean {
        if (message.userId == null || message.channelId != id()) {
            return false
        }
        send(message.userId, message, false)
        return true
    }

    private fun accept(update: Update): Boolean {
        if (update.hasMessage()) {
            val sender = update.message.chat.userName ?: return false
            if (senderWhitelist.isEmpty() || senderWhitelist.contains(sender)) {
                return true
            } else {
                LOGGER.warn("Unauthorized sender: $sender")
                return false
            }
        }
        return false
    }

    private fun typing(chatId: String) {
        val action = SendChatAction.builder()
            .chatId(chatId)
            .action(ActionType.TYPING.toString())
            .build()
        client.execute(action)
    }

    private fun send(chatId: String, message: Message, notification: Boolean) {
        val html = MarkdownToTelegramHTML.convert(message.text)
        val sendMessage = SendMessage.builder()
            .chatId(chatId)
            .text(html)
            .parseMode(ParseMode.HTML)
            .disableNotification(!notification)
            .build()
        client.execute(sendMessage)

        message.filePaths.forEach { path ->
            try {
                LOGGER.info("Sending $path to $chatId")
                sendFile(chatId, path)
            } catch (ex: Exception) {
                LOGGER.warn("Failed to send file $path to chat $chatId", ex)
            }
        }
    }

    private fun sendFile(chatId: String, path: String) {
        val file = File(path)
        val sendDocument = SendDocument.builder()
            .chatId(chatId)
            .document(InputFile(file, file.name))
            .build()
        client.execute(sendDocument)
    }

    private fun download(doc: Document): File {
        val fileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=${doc.fileId}"
        val response = rest.getForEntity(fileUrl, Map::class.java).body!!
        val path = MapUtil.toMap("result", response)?.get("file_path")?.toString()
            ?: throw IllegalStateException("file_path not found")

        val xpath = if (path.startsWith("/")) path else "/$path"
        val contentUrl = "https://api.telegram.org/file/bot$botToken$xpath"
        val content = rest.getForEntity(contentUrl, ByteArray::class.java).body!!

        return context.fileService.create(doc.fileName, content)
    }
}
