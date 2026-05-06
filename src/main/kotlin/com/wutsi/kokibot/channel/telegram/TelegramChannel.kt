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
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TelegramChannel(
    assistant: Assistant,
    val factory: TelegramFactory = TelegramFactory(),
    val restBuilder: RestBuilder = RestBuilder(),
) : Channel(assistant), LongPollingSingleThreadUpdateConsumer {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramChannel::class.java)

        const val ID = "channel:telegram"
        const val TYPING_DELAY_MILLIS = 2000L
        const val MAX_LENGTH = 3840 // Max is 4K, but reserve some for HTML tags
        const val STREAM_MAX_LENGTH = 100
        const val ERROR_UNSUPPORTED_MESSAGE = "Sorry, I can only process text messages and documents for now."
        const val ERROR_UNAUTHORIZED_MESSAGE = "Sorry, you are not authorized to interact with me."
    }

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(4)
    private lateinit var app: TelegramBotsLongPollingApplication
    private lateinit var client: TelegramClient
    private lateinit var botToken: String
    private lateinit var context: Context
    private lateinit var rest: RestTemplate
    private lateinit var senderWhitelist: List<String>

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.botToken = config["token"]?.toString()
            ?: throw ConfigurationException("token is required")

        client = factory.createTelegramClient(botToken)
        rest = restBuilder.build(30000L, 30000L)
        senderWhitelist = MapUtil.toList("sender-whitelist", config)
            ?.mapNotNull { entry -> entry?.toString() }
            ?: emptyList()
        this.context = context

        // Register the bot after initialization
        app = factory.createTelegramBotsLongPollingApplication()
        app.registerBot(botToken, this)
    }

    override fun destroy() {
        try {
            app.unregisterBot(botToken)
        } catch (e: Exception) {
            LOGGER.warn("Error while disconnecting from Telegram", e)
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

    override fun send(message: Message): Boolean {
        if (message.userId == null || message.channelId != id()) {
            return false
        }
        send(message.userId, message, false)
        return true
    }

    override fun consume(update: Update) {
        /* Process the message */
        if (update.hasMessage()) {
            val message = update.message
            val chatId = message.chatId.toString()

            /* Check sender whitelist */
            if (!accept(message)) {
                send(chatId, Message(text = ERROR_UNAUTHORIZED_MESSAGE), true)
                return
            }

            /* Typing indicator */
            val task = Runnable {
                typing(chatId)
            }
            val job = scheduler.scheduleAtFixedRate(task, 0, TYPING_DELAY_MILLIS, TimeUnit.MILLISECONDS)

            /* Consume the message asynchronously */
            try {
                consume(chatId, update)
            } finally {
                job.cancel(true)
            }
        }
    }

    private fun consume(chatId: String, update: Update) {
        val message = if (update.message.hasText()) {
            consumeText(update)
        } else if (update.message.hasDocument()) {
            consumeDocument(update)
        } else if (update.message.hasPhoto()) {
            consumePhoto(update)
        } else {
            Message(
                text = ERROR_UNSUPPORTED_MESSAGE,
                role = Role.SYSTEM,
            )
        }

        send(chatId, message, true)
    }

    private fun consumeText(update: Update): Message {
        val chatId = update.message.chatId.toString()
        val userId = update.message.chat.id.toString()
        var streamMessageId: Int? = null
        val streamBuffer = StringBuilder()
        var lastUpdateTime = System.currentTimeMillis()

        try {
            return assistant.process(
                Message(
                    text = toText(update.message.text, update.message.isCommand),
                    role = Role.USER,
                    userId = userId,
                    channelId = id(),
                ),
                streamCallback = { delta ->
                    streamBuffer.append(delta)
                    val now = System.currentTimeMillis()

                    if ((streamBuffer.length % 50 == 0 || now - lastUpdateTime > 500) && streamBuffer.isNotEmpty()) {
                        val msg = streamBuffer.toString().takeLast(STREAM_MAX_LENGTH)
                        try {
                            streamMessageId = sendOrUpdateMessage(
                                chatId,
                                "**Thinking...**: $msg",
                                streamMessageId
                            )
                            lastUpdateTime = now
                        } catch (ex: Exception) {
                            LOGGER.warn(
                                "Failed to send or update streaming message, will retry on next update",
                                ex
                            )
                            if (streamMessageId != null) {
                                deleteMessage(chatId, streamMessageId!!)
                            }
                            streamMessageId = null // Invalidate message ID to trigger sending a new message
                        }
                    }
                }
            )
        } finally {
            if (streamMessageId != null) {
                deleteMessage(chatId, streamMessageId!!)
            }
        }
    }

    private fun consumeDocument(update: Update): Message {
        val userId = update.message.chat.id.toString()
        val fileId = update.message.document.fileId
        val filename = update.message.document.fileName
        val caption = update.message.caption?.trim()?.ifEmpty { null }
        val file = download(fileId, filename)

        return assistant.process(
            Message(
                text = toText(
                    caption
                        ?: "File received: $filename. Do not process this document, just return the message `File received`",
                    update.message.isCommand
                ),
                role = Role.USER,
                userId = userId,
                channelId = id(),
                filePaths = listOf(file.absolutePath)
            )
        )
    }

    private fun consumePhoto(update: Update): Message {
        // Telegram sends multiple resolutions; pick the largest
        val largest = update.message.photo.maxByOrNull { it.fileSize ?: 0 }
            ?: throw IllegalStateException("No photo found in message")

        val userId = update.message.chat.id.toString()
        val caption = update.message.caption?.trim()?.ifEmpty { null }
        val filename = "photo_${largest.fileId}.jpg"
        val file = download(largest.fileId, filename)

        return assistant.process(
            Message(
                text = toText(
                    caption
                        ?: "Image received: $filename. Do not process this document, just return the message `File received`",
                    update.message.isCommand
                ),
                role = Role.USER,
                userId = userId,
                channelId = id(),
                filePaths = listOf(file.absolutePath),
            ),
        )
    }

    private fun accept(message: org.telegram.telegrambots.meta.api.objects.message.Message): Boolean {
        val sender = message.chat.userName ?: return false
        if (senderWhitelist.isEmpty() || senderWhitelist.contains(sender)) {
            return true
        } else {
            LOGGER.warn("Unauthorized sender: $sender")
            return false
        }
    }

    private fun typing(chatId: String) {
        val action = SendChatAction.builder()
            .chatId(chatId)
            .action(ActionType.TYPING.toString())
            .build()
        client.execute(action)
    }

    private fun send(chatId: String, message: Message, notification: Boolean) {
        val html = MarkdownToTelegramHTML.convert(message.text.takeLast(MAX_LENGTH))
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

    private fun deleteMessage(chatId: String, messageId: Int) {
        try {
            val deleteMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build()
            client.execute(deleteMessage)
        } catch (ex: Exception) {
            LOGGER.warn("Failed to delete message $messageId in chat $chatId", ex)
        }
    }

    private fun toText(text: String, command: Boolean): String {
        if (command) {
            return text
        }

        return "$text\n" +
            "Do not include table or grid in your response, as they are not well supported in Telegram. " +
            "Instead, please format the response as a nested bulleted list if you need to express hierarchy or relationships"
    }

    /**
     * Sends a new message or updates an existing one (for streaming).
     * Telegram supports editing messages, so we can update the same message incrementally.
     *
     * @return Message ID of the sent/updated message
     */
    private fun sendOrUpdateMessage(
        chatId: String,
        text: String,
        messageId: Int?,
    ): Int? {
        if (text.trim().isEmpty()) {
            return messageId
        }

        val html = MarkdownToTelegramHTML.convert(text.takeLast(MAX_LENGTH))

        if (messageId == null) {
            val sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(html)
                .parseMode(ParseMode.HTML)
                .disableNotification(true)
                .build()
            val sent = client.execute(sendMessage)
            return sent.messageId
        } else {
            val editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(html)
                .parseMode(ParseMode.HTML)
                .build()
            client.execute(editMessage)
            return messageId
        }
    }

    private fun download(fileId: String, fileName: String): File {
        val fileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        val response = rest.getForEntity(fileUrl, Map::class.java).body!!
        val path = MapUtil.toMap("result", response)?.get("file_path")?.toString()
            ?: throw IllegalStateException("file_path not found")

        val xpath = if (path.startsWith("/")) path else "/$path"
        val contentUrl = "https://api.telegram.org/file/bot$botToken$xpath"
        val content = rest.getForEntity(contentUrl, ByteArray::class.java).body!!

        return context.fileService.create(fileName, content)
    }
}
