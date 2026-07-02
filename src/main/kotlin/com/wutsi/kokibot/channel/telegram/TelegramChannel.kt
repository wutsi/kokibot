package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.MarkdownUtil
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
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File

class TelegramChannel(
    val factory: TelegramFactory = TelegramFactory(),
    val restBuilder: RestBuilder = RestBuilder(),
    val users: TelegramUsers = TelegramUsers(),
) : Channel(), LongPollingSingleThreadUpdateConsumer {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramChannel::class.java)

        const val MESSAGE_MAX_LENGTH = 3900 // Telegram max is 4096, but we reserve some for HTML tags
        const val ERROR_UNSUPPORTED_MESSAGE = "Sorry, I can only process text messages and documents for now."
        const val ERROR_UNAUTHORIZED_MESSAGE = "Sorry, you are not authorized to interact with me."
    }

    private var botToken: String? = null
    private lateinit var botName: String
    private lateinit var app: TelegramBotsLongPollingApplication
    private lateinit var client: TelegramClient
    private lateinit var context: Context
    private lateinit var rest: RestTemplate
    private lateinit var senderWhitelist: List<String>

    override fun name(): String = "telegram"

    override fun source(): String = botName

    @Synchronized
    override fun init(config: Map<*, *>, context: Context) {
        val token = context.credentialService.get("channel.telegram")
        this.botToken = token
        this.botName = config["bot-name"]?.toString() ?: "-"

        client = factory.createTelegramClient(token)
        rest = restBuilder.build(30000L, 30000L)
        senderWhitelist = MapUtil.toList("sender-whitelist", config)
            ?.mapNotNull { entry -> entry?.toString() }
            ?: emptyList()
        this.context = context

        users.init(context)

        LOGGER.info("Channel: telegram")
        LOGGER.info("  bot-name: $botName")
        LOGGER.info("  sender-whitelist: $senderWhitelist")

        app = factory.createTelegramBotsLongPollingApplication()
        app.registerBot(token, this)
    }

    @Synchronized
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

    override fun sendStatus(message: Message) {
        if (message.userId == null || message.channelId != id()) return
        val chatId = users.get(message.userId) ?: return
        val action = SendChatAction.builder()
            .chatId(chatId)
            .action(ActionType.TYPING.toString())
            .build()
        client.execute(action)
    }

    override fun send(message: Message): Boolean {
        if (message.userId == null || message.channelId != id()) {
            return false
        }

        users.get(message.userId)?.let { chatId ->
            send(chatId, message)
            return true
        }
        return false
    }

    override fun consume(update: Update) {
        if (!update.hasMessage()) return

        val chatId = update.message.chatId.toString()
        if (!accept(update)) {
            send(chatId, Message(text = ERROR_UNAUTHORIZED_MESSAGE))
            return
        }

        try {
            storeUser(update)
        } catch (ex: Exception) {
            LOGGER.warn("Failed to store user info for chat $chatId. ${ex.message}")
        }

        when {
            update.message.hasText() -> consumeText(update)
            update.message.hasDocument() -> consumeDocument(update)
            update.message.hasPhoto() -> consumePhoto(update)
            else -> send(chatId, Message(text = ERROR_UNSUPPORTED_MESSAGE, role = Role.SYSTEM))
        }
    }

    private fun storeUser(update: Update) {
        val userId = toUserId(update)
        users.put(userId, update.message.chatId.toString())
    }

    private fun consumeText(update: Update) {
        val userId = toUserId(update)
        context.inbox.submit(
            Message(
                text = update.message.text,
                role = Role.USER,
                userId = userId,
                channelId = id(),
            )
        )
    }

    private fun consumeDocument(update: Update) {
        val userId = toUserId(update)
        val fileId = update.message.document.fileId
        val filename = update.message.document.fileName
        val caption = update.message.caption?.trim()?.ifEmpty { null }
        val file = download(fileId, filename)
        context.inbox.submit(
            Message(
                text = caption
                    ?: "File received: $filename. Do not process this document, just return the message `File received`",
                role = Role.USER,
                userId = userId,
                channelId = id(),
                filePaths = listOf(file.absolutePath),
            )
        )
    }

    private fun consumePhoto(update: Update) {
        val largest = update.message.photo.maxByOrNull { it.fileSize ?: 0 }
            ?: throw IllegalStateException("No photo found in message")
        val userId = toUserId(update)
        val caption = update.message.caption?.trim()?.ifEmpty { null }
        val filename = "photo_${largest.fileId}.jpg"
        val file = download(largest.fileId, filename)
        context.inbox.submit(
            Message(
                text = caption
                    ?: "Image received: $filename. Do not process this document, just return the message `File received`",
                role = Role.USER,
                userId = userId,
                channelId = id(),
                filePaths = listOf(file.absolutePath),
            )
        )
    }

    private fun toUserId(update: Update): String {
        val chat = update.message.chat
        return chat.userName ?: chat.id.toString()
    }

    private fun accept(update: Update): Boolean {
        val sender = toUserId(update)
        if (senderWhitelist.isEmpty() || senderWhitelist.contains(sender)) {
            return true
        } else {
            LOGGER.warn("Unauthorized sender: $sender")
            return false
        }
    }

    private fun send(chatId: String, message: Message) {
        if (message.text.ifEmpty { null } != null) {
            val parts = MarkdownUtil.split(message.text, MESSAGE_MAX_LENGTH)
            parts.forEachIndexed { index, part ->
                val html = MarkdownToTelegramHTML.convert(part)
                val sendMessage = SendMessage
                    .builder()
                    .chatId(chatId)
                    .text(html)
                    .parseMode(ParseMode.HTML)
                    .disableNotification(index == 0)
                    .build()
                client.execute(sendMessage)
            }
        }

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

    private fun download(fileId: String, filename: String): File {
        val fileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        val response = rest.getForEntity(fileUrl, Map::class.java).body!!
        val path = MapUtil.toMap("result", response)?.get("file_path")?.toString()
            ?: throw IllegalStateException("file_path not found")

        val xpath = if (path.startsWith("/")) path else "/$path"
        val contentUrl = "https://api.telegram.org/file/bot$botToken$xpath"
        val content = rest.getForEntity(contentUrl, ByteArray::class.java).body!!

        val file = context.fileService.createTempFile(filename)
        file.writeBytes(content)
        return file
    }
}
