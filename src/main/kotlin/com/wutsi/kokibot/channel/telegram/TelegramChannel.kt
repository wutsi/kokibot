package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.exception.ConfigurationException
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File
import java.time.LocalDate
import java.util.UUID

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
            if (response?.get("ok") == true) {
                return Health(id(), true)
            } else {
                return Health(id(), false, "Unhealthy")
            }
        } catch (ex: Exception) {
            LOGGER.warn("error during telegram channel heath", ex)
            return Health(id(), false, ex.message ?: "Unhealthy")
        }
    }

    override fun consume(update: Update) {
        if (update.hasMessage() && accept(update)) {
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
            val userId = "${update.message.chat.userName}@telegram"
            val message = try {
                if (update.message.hasText()) {
                    assistant.process(
                        Message(
                            text = update.message.text,
                            role = Role.USER,
                            userId = userId,
                        )
                    )
                } else if (update.message.hasDocument()) {
                    val file = download(update.message.document.fileId)
                    assistant.process(
                        Message(
                            text = "File received: ${update.message.document.fileName}. Do not process this document, just return the message `File received`",
                            role = Role.USER,
                            userId = userId,
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
            send(chatId, message)
        }
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

    private fun send(chatId: String, message: Message) {
        val html = MarkdownToTelegramHTML.convert(message.text)
        println(message.text)
        println()
        println(html)
        val sendMessage = SendMessage.builder()
            .chatId(chatId)
            .text(html)
            .parseMode(ParseMode.HTML)
            .build()
        client.execute(sendMessage)
    }

    private fun download(fileId: String): File {
        val fileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        val response = rest.getForEntity(fileUrl, Map::class.java).body!!
        val path = MapUtil.toMap("result", response)?.get("file_path")?.toString()
            ?: throw IllegalStateException("file_path not found")

        val xpath = if (path.startsWith("/")) path else "/$path"
        val contentUrl = "https://api.telegram.org/file/bot$botToken$xpath"
        val content = rest.getForEntity(contentUrl, ByteArray::class.java).body!!

        val now = LocalDate.now()
        val file = File(context.home.absolutePath + "/workspace/telegram/files/$now/${UUID.randomUUID()}/$path")
        file.parentFile.mkdirs()
        file.writeBytes(content)
        return file
    }
}
