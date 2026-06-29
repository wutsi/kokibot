package com.wutsi.kokibot.channel.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.service.inbox.Inbox
import com.wutsi.kokibot.util.RestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.exceptions.TelegramApiErrorResponseException
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Document
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramChannelTest {
    private val app = mock<TelegramBotsLongPollingApplication>()
    private val client = mock<TelegramClient>()
    private val factory = mock<TelegramFactory>()
    private val rest = mock<RestTemplate>()
    private val restBuilder = mock<RestBuilder>()
    private val botToken = "13200493:AAH-abc123def456ghi789jkl012mno345pqr"
    private val config = mapOf(
        "token" to botToken,
        "bot-name" to "test-bot",
    )
    private val inbox = mock<Inbox>()
    private val context = Context(
        home = File("target/test-data/telegram"),
        llm = mock(),
        fileService = mock(),
        assistant = mock(),
        inbox = inbox,
    )
    private val users = mock<TelegramUsers>()
    private val telegram = TelegramChannel(factory, restBuilder, users)

    @BeforeEach
    fun setUp() {
        doReturn(client).whenever(factory).createTelegramClient(any())
        doReturn(app).whenever(factory).createTelegramBotsLongPollingApplication()
        doReturn(rest).whenever(restBuilder).build(anyOrNull(), anyOrNull())
    }

    @Test
    fun id() {
        assertEquals("channel:telegram", telegram.id())
    }

    @Test
    fun source() {
        telegram.init(config, context)

        assertEquals("test-bot", telegram.source())
    }

    @Test
    fun `init - should create telegram client and register bot`() {
        telegram.init(config, context)

        verify(factory).createTelegramClient(botToken)
        verify(factory).createTelegramBotsLongPollingApplication()
        verify(app).registerBot(any(), any())
        verify(users).init(context)
    }

    @Test
    fun `init - should throw exception when token is missing`() {
        assertThrows<ConfigurationException> {
            telegram.init(emptyMap<String, Any>(), context)
        }
    }

    @Test
    fun `init - should throw exception on invalid token`() {
        doThrow(TelegramApiErrorResponseException::class).whenever(app).registerBot(any(), any())
        assertThrows<TelegramApiErrorResponseException> {
            telegram.init(config, context)
        }
    }

    @Test
    fun `destroy - should stop the application`() {
        telegram.init(config, context)

        telegram.destroy()

        verify(app).unregisterBot(botToken)
    }

    @Test
    fun `destroy - should do nothing if not initialized`() {
        telegram.destroy()

        verify(app, never()).unregisterBot(any())
    }

    @Test
    fun `consume - should submit text message to inbox`() {
        telegram.init(config, context)

        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)

        val prompt = argumentCaptor<Message>()
        verify(inbox).submit(prompt.capture())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("ray.sponsible", prompt.firstValue.userId)
        assertEquals("channel:telegram", prompt.firstValue.channelId)
        assertEquals(emptyList<String>(), prompt.firstValue.filePaths)

        verify(client, never()).execute(any<SendMessage>())
        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should submit message from username-less user to inbox`() {
        telegram.init(config, context)

        val update = createTextUpdate("Hello", 123L, null)
        telegram.consume(update)

        val prompt = argumentCaptor<Message>()
        verify(inbox).submit(prompt.capture())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("123", prompt.firstValue.userId)
        assertEquals("channel:telegram", prompt.firstValue.channelId)

        verify(users).put("123", "123")
    }

    @Test
    fun `consume - should submit to inbox even if error while storing users`() {
        telegram.init(config, context)
        doThrow(IllegalArgumentException::class).whenever(users).put(any(), any())

        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)

        val prompt = argumentCaptor<Message>()
        verify(inbox).submit(prompt.capture())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("ray.sponsible", prompt.firstValue.userId)
    }

    @Test
    fun `consume - with username white-listed - accepted`() {
        val config = this.config + mapOf("sender-whitelist" to listOf("ray.sponsible"))
        telegram.init(config, context)

        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)

        verify(inbox).submit(any())
    }

    @Test
    fun `consume - with chatId white-listed - accepted`() {
        val config = this.config + mapOf("sender-whitelist" to listOf("123"))
        telegram.init(config, context)

        val update = createTextUpdate("Hello", 123L, null)
        telegram.consume(update)

        verify(inbox).submit(any())
    }

    @Test
    fun `consume - with whitelist - rejected`() {
        val config = this.config + mapOf("sender-whitelist" to listOf("roger.milla"))
        telegram.init(config, context)

        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)

        verify(inbox, never()).submit(any())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals(TelegramChannel.ERROR_UNAUTHORIZED_MESSAGE, sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - should submit document to inbox`() {
        val update = createDocumentUpdate(123L, fileId = "21093209", "foo.pdf")
        doReturn(ResponseEntity(mapOf("result" to mapOf("file_path" to "/files/1.pdf")), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), any<Class<*>>())

        doReturn(ResponseEntity("Hello world".toByteArray(), HttpStatus.OK))
            .whenever(rest)
            .getForEntity("https://api.telegram.org/file/bot$botToken/files/1.pdf", ByteArray::class.java)

        val file = File(this::class.java.getResource("/file/document-en.pdf")!!.file)
        doReturn(file).whenever(context.fileService).createTempFile(any())

        telegram.init(config, context)
        telegram.consume(update)

        val prompt = argumentCaptor<Message>()
        verify(inbox).submit(prompt.capture())
        assertNotNull(prompt.firstValue.text)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(1, prompt.firstValue.filePaths.size)
        assertEquals(file.absolutePath, prompt.firstValue.filePaths[0])

        verify(context.fileService).createTempFile("foo.pdf")
        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should submit photo to inbox`() {
        val update = createPhotoUpdate(123L, "2222", "Analyze this...")
        doReturn(ResponseEntity(mapOf("result" to mapOf("file_path" to "/files/1.png")), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), any<Class<*>>())

        doReturn(ResponseEntity("Hello world".toByteArray(), HttpStatus.OK))
            .whenever(rest)
            .getForEntity("https://api.telegram.org/file/bot$botToken/files/1.png", ByteArray::class.java)

        val file = File(this::class.java.getResource("/file/medic.png")!!.file)
        doReturn(file).whenever(context.fileService).createTempFile(any())

        telegram.init(config, context)
        telegram.consume(update)

        val prompt = argumentCaptor<Message>()
        verify(inbox).submit(prompt.capture())
        assertNotNull(prompt.firstValue.text)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(1, prompt.firstValue.filePaths.size)
        assertEquals(file.absolutePath, prompt.firstValue.filePaths[0])

        verify(context.fileService).createTempFile("photo_2222.jpg")
        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should send error for unsupported message type`() {
        telegram.init(config, context)

        val update = createTextUpdate(null, 4309)
        telegram.consume(update)

        verify(inbox, never()).submit(any())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals(TelegramChannel.ERROR_UNSUPPORTED_MESSAGE, sendMessage.firstValue.text)
        assertEquals("4309", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - should ignore update without message`() {
        telegram.init(config, context)

        val update = createEmptyUpdate(555)
        telegram.consume(update)

        verify(inbox, never()).submit(any())
        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun sendStatus() {
        doReturn("123").whenever(users).get("ray.sponsible")

        telegram.init(config, context)
        telegram.sendStatus(Message(userId = "ray.sponsible", channelId = "channel:telegram", text = "thinking..."))

        val action = argumentCaptor<SendChatAction>()
        verify(client).execute(action.capture())
        assertEquals(ActionType.TYPING.toString(), action.firstValue.action)
        assertEquals("123", action.firstValue.chatId)
    }

    @Test
    fun `sendStatus - no userId`() {
        telegram.init(config, context)
        telegram.sendStatus(Message(userId = null, channelId = "channel:telegram", text = "thinking..."))

        verify(client, never()).execute(any<SendChatAction>())
    }

    @Test
    fun `sendStatus - bad channelId`() {
        telegram.init(config, context)
        telegram.sendStatus(Message(userId = "ray.sponsible", channelId = "xxx", text = "thinking..."))

        verify(client, never()).execute(any<SendChatAction>())
    }

    @Test
    fun `sendStatus - unknown user`() {
        doReturn(null).whenever(users).get("ray.sponsible")

        telegram.init(config, context)
        telegram.sendStatus(Message(userId = "ray.sponsible", channelId = "channel:telegram", text = "thinking..."))

        verify(client, never()).execute(any<SendChatAction>())
    }

    @Test
    fun send() {
        doReturn("123").whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "ray.sponsible",
            channelId = "channel:telegram",
            text = "Hello"
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertTrue(result)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("Hello", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `send with document`() {
        doReturn("123").whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "ray.sponsible",
            channelId = "channel:telegram",
            text = "Hello",
            filePaths = listOf("/path/to/file.pdf", "/path/to/file.docx")
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertTrue(result)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("Hello", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)

        val sendDocument = argumentCaptor<SendDocument>()
        verify(client, com.nhaarman.mockitokotlin2.times(2)).execute(sendDocument.capture())
        assertEquals("123", sendDocument.firstValue.chatId)
        assertEquals("file.pdf", sendDocument.firstValue.document.newMediaFile.name)
        assertEquals("123", sendDocument.secondValue.chatId)
        assertEquals("file.docx", sendDocument.secondValue.document.newMediaFile.name)
    }

    @Test
    fun `send - no userId`() {
        val message = Message(
            userId = null,
            channelId = "channel:telegram",
            text = "Hello"
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertFalse(result)
        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `send - bad userId`() {
        doReturn(null).whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "xxxx",
            channelId = "channel:telegram",
            text = "Hello"
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertFalse(result)
        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `send - bad channelId`() {
        doReturn(null).whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "ray.sponsible",
            channelId = "xxx",
            text = "Hello"
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertFalse(result)
        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `health - up`() {
        doReturn(ResponseEntity(mapOf("ok" to true), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), any<Class<*>>())

        telegram.init(config, context)
        val result = telegram.health()

        assertEquals(true, result.up)
        assertEquals("channel:telegram", result.id)
        assertEquals(0, result.children.size)
    }

    @Test
    fun `health - down`() {
        doReturn(ResponseEntity(mapOf("ok" to "---"), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), any<Class<*>>())

        telegram.init(config, context)
        val result = telegram.health()

        assertEquals(false, result.up)
        assertEquals("channel:telegram", result.id)
        assertEquals(0, result.children.size)
    }

    @Test
    fun `health - error`() {
        doThrow(RuntimeException::class)
            .whenever(rest)
            .getForEntity(any<String>(), any<Class<*>>())

        val result = telegram.health()

        assertEquals(false, result.up)
        assertEquals("channel:telegram", result.id)
        assertEquals(0, result.children.size)
    }

    private fun createDocumentUpdate(chatId: Long, fileId: String, fileName: String): Update {
        val chat = Chat(chatId, "")
        chat.userName = "ray.sponsible"
        chat.firstName = "Ray"
        chat.lastName = "Responsible"

        val document = Document()
        document.fileId = fileId
        document.fileName = fileName

        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = chat
        message.document = document
        update.message = message
        return update
    }

    private fun createTextUpdate(text: String?, chatId: Long, username: String? = "ray.sponsible"): Update {
        val chat = Chat(chatId, "")
        chat.userName = username
        chat.firstName = "Ray"
        chat.lastName = "Responsible"

        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = chat
        message.text = text
        update.message = message
        return update
    }

    private fun createPhotoUpdate(chatId: Long, photoId: String, text: String?): Update {
        val chat = Chat(chatId, "")
        chat.userName = "ray.sponsible"
        chat.firstName = "Ray"
        chat.lastName = "Responsible"

        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = chat
        message.caption = text
        message.photo = listOf(
            PhotoSize().apply {
                fileId = photoId
                fileSize = 10000
            },
        )
        update.message = message
        return update
    }

    private fun createEmptyUpdate(chatId: Long): Update {
        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = Chat(chatId, "")
        return update
    }
}
