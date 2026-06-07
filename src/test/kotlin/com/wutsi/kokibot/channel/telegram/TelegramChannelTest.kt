package com.wutsi.kokibot.channel.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeast
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.util.RestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.http.HttpEntity
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
    private val config = mapOf("token" to botToken)
    private val context = Context(
        home = File("target/test-data/telegram"),
        llm = mock(),
        fileService = mock(),
        assistant = mock()
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
        assertEquals(TelegramChannel.ID, telegram.id())
    }

    @Test
    fun `init - should create telegram client and register bot`() {
        // WHEN
        telegram.init(config, context)

        // THEN
        verify(factory).createTelegramClient(botToken)
        verify(factory).createTelegramBotsLongPollingApplication()
        verify(app).registerBot(eq(botToken), any())
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
        // GIVEN
        telegram.init(config, context)

        // WHEN
        telegram.destroy()

        // THEN
        verify(app).unregisterBot(botToken)
    }

    @Test
    fun `destroy - should do nothing if not initialized`() {
        // WHEN
        telegram.destroy()

        // THEN
        verify(app, never()).unregisterBot(any())
    }

    @Test
    fun `consume - should process text message`() {
        // GIVEN
        telegram.init(config, context)
        doAnswer {
            Thread.sleep(TelegramChannel.TYPING_DELAY_MILLIS)
            Message("World")
        }.whenever(context.assistant).process(any(), anyOrNull())

        // WHEN
        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)
        Thread.sleep(3000) // Wait for the async processing to complete

        // THEN
        val prompt = argumentCaptor<Message>()
        verify(context.assistant).process(prompt.capture(), anyOrNull())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("ray.sponsible", prompt.firstValue.userId)
        assertEquals(TelegramChannel.ID, prompt.firstValue.channelId)
        assertEquals(emptyList<String>(), prompt.firstValue.filePaths)

        val sendAction = argumentCaptor<SendChatAction>()
        verify(client, atLeast(1)).execute(sendAction.capture())
        assertEquals(ActionType.TYPING.toString(), sendAction.firstValue.action)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)

        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should process message from username-less user`() {
        // GIVEN
        telegram.init(config, context)
        doReturn(Message("World"))
            .whenever(context.assistant)
            .process(any(), anyOrNull())

        // WHEN
        val update = createTextUpdate("Hello", 123L, null)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        val prompt = argumentCaptor<Message>()
        verify(context.assistant).process(prompt.capture(), anyOrNull())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("123", prompt.firstValue.userId)
        assertEquals(TelegramChannel.ID, prompt.firstValue.channelId)
        assertEquals(emptyList<String>(), prompt.firstValue.filePaths)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)

        verify(users).put("123", "123")
    }

    @Test
    fun `consume - should process text message even if error while storing users`() {
        // GIVEN
        telegram.init(config, context)
        doAnswer {
            Thread.sleep(TelegramChannel.TYPING_DELAY_MILLIS)
            Message("World")
        }.whenever(context.assistant).process(any(), anyOrNull())

        doThrow(IllegalArgumentException::class).whenever(users).put(any(), any())

        // WHEN
        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)
        Thread.sleep(3000) // Wait for the async processing to complete

        // THEN
        val prompt = argumentCaptor<Message>()
        verify(context.assistant).process(prompt.capture(), anyOrNull())
        assertEquals(true, prompt.firstValue.text.contains("Hello"))
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("ray.sponsible", prompt.firstValue.userId)
        assertEquals(TelegramChannel.ID, prompt.firstValue.channelId)
        assertEquals(emptyList<String>(), prompt.firstValue.filePaths)

        val sendAction = argumentCaptor<SendChatAction>()
        verify(client, atLeast(1)).execute(sendAction.capture())
        assertEquals(ActionType.TYPING.toString(), sendAction.firstValue.action)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - with username white-listed - accepted`() {
        // GIVEN
        doReturn(Message("World")).whenever(context.assistant).process(any(), anyOrNull())

        val config = this.config + mapOf("sender-whitelist" to listOf("ray.sponsible"))
        telegram.init(config, context)

        // WHEN
        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(context.assistant).process(any(), anyOrNull())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - with chatId white-listed - accepted`() {
        // GIVEN
        doReturn(Message("World"))
            .whenever(context.assistant)
            .process(any(), anyOrNull())

        val config = this.config + mapOf("sender-whitelist" to listOf("123"))
        telegram.init(config, context)

        // WHEN
        val update = createTextUpdate("Hello", 123L, null)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(context.assistant).process(any(), anyOrNull())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - with whitelist - rejected`() {
        // GIVEN
        val config = this.config + mapOf("sender-whitelist" to listOf("roger.milla"))
        telegram.init(config, context)

        // WHEN
        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)

        // THEN
        verify(context.assistant, never()).process(any(), anyOrNull())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals(TelegramChannel.ERROR_UNAUTHORIZED_MESSAGE, sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - should process documents`() {
        // GIVEN
        doReturn(Message("Received")).whenever(context.assistant).process(any(), anyOrNull())

        val update = createDocumentUpdate(123L, fileId = "21093209", "foo.pdf")
        doReturn(ResponseEntity(mapOf("result" to mapOf("file_path" to "/files/1.pdf")), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), eq(Map::class.java))

        doReturn(ResponseEntity("Hello world".toByteArray(), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), eq(ByteArray::class.java))

        val file = File(this::class.java.getResource("/file/document-en.pdf")!!.file)
        doReturn(file).whenever(context.fileService).createTempFile(any())

        // WHEN
        telegram.init(config, context)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(rest).getForEntity("https://api.telegram.org/bot$botToken/getFile?file_id=21093209", Map::class.java)
        verify(rest).getForEntity("https://api.telegram.org/file/bot$botToken/files/1.pdf", ByteArray::class.java)

        val prompt = argumentCaptor<Message>()
        verify(context.assistant).process(prompt.capture(), anyOrNull())
        assertNotNull(prompt.firstValue.text)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(1, prompt.firstValue.filePaths.size)
        assertEquals(file.absolutePath, prompt.firstValue.filePaths[0])

        verify(context.fileService).createTempFile("foo.pdf")

        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should process photo`() {
        // GIVEN
        doReturn(Message("Received")).whenever(context.assistant).process(any(), anyOrNull())

        val update = createPhotoUpdate(123L, "2222", "Analyze this...")
        doReturn(ResponseEntity(mapOf("result" to mapOf("file_path" to "/files/1.png")), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), eq(Map::class.java))

        doReturn(ResponseEntity("Hello world".toByteArray(), HttpStatus.OK))
            .whenever(rest)
            .getForEntity(any<String>(), eq(ByteArray::class.java))

        val file = File(this::class.java.getResource("/file/medic.png")!!.file)
        doReturn(file).whenever(context.fileService).createTempFile(any())

        // WHEN
        telegram.init(config, context)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(rest).getForEntity("https://api.telegram.org/bot$botToken/getFile?file_id=2222", Map::class.java)
        verify(rest).getForEntity("https://api.telegram.org/file/bot$botToken/files/1.png", ByteArray::class.java)

        val prompt = argumentCaptor<Message>()
        verify(context.assistant).process(prompt.capture(), anyOrNull())
        assertNotNull(prompt.firstValue.text)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(1, prompt.firstValue.filePaths.size)
        assertEquals(file.absolutePath, prompt.firstValue.filePaths[0])

        verify(context.fileService).createTempFile(eq("photo_2222.jpg"))

        verify(users).put("ray.sponsible", "123")
    }

    @Test
    fun `consume - should ignore update without message text`() {
        // GIVEN
        telegram.init(config, context)

        // WHEN
        val update = createTextUpdate(null, 4309)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(context.assistant, never()).process(any(), anyOrNull())

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals(TelegramChannel.ERROR_UNSUPPORTED_MESSAGE, sendMessage.firstValue.text)
        assertEquals("4309", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - should ignore update without message`() {
        // GIVEN
        telegram.init(config, context)

        // WHEN
        val update = createEmptyUpdate(555)
        telegram.consume(update)

        // THEN
        verify(context.assistant, never()).process(any(), anyOrNull())
        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `consume - long response are slitted`() {
        // GIVEN
        telegram.init(config, context)
        doReturn(
            Message(
                "H".repeat(3000) + "\n\n" + "W".repeat(1000)
            )
        )
            .whenever(context.assistant)
            .process(any(), anyOrNull())

        // WHEN
        val update = createTextUpdate("Hello", 123L)
        telegram.consume(update)
        Thread.sleep(1000) // Wait for the async processing to complete

        // THEN
        verify(client, times(2)).execute(any<SendMessage>())
    }

    @Test
    fun send() {
        doReturn("123").whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "ray.sponsible",
            channelId = TelegramChannel.ID,
            text = "Hello"
        )
        telegram.init(config, context)
        val result = telegram.send(message)

        assertTrue(result)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("Hello", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)

        verify(rest, never())
            .postForEntity(any<String>(), any<HttpEntity<*>>(), eq(String::class.java))
    }

    @Test
    fun `send with document`() {
        doReturn("123").whenever(users).get("ray.sponsible")

        val message = Message(
            userId = "ray.sponsible",
            channelId = TelegramChannel.ID,
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
        verify(client, times(2)).execute(sendDocument.capture())
        assertEquals("123", sendDocument.firstValue.chatId)
        assertEquals("file.pdf", sendDocument.firstValue.document.newMediaFile.name)
        assertEquals("123", sendDocument.secondValue.chatId)
        assertEquals("file.docx", sendDocument.secondValue.document.newMediaFile.name)
    }

    @Test
    fun `send - no userId`() {
        val message = Message(
            userId = null,
            channelId = TelegramChannel.ID,
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
            channelId = TelegramChannel.ID,
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
            .getForEntity(any<String>(), eq(Map::class.java))

        telegram.init(config, context)
        val result = telegram.health()

        assertEquals(true, result.up)
        assertEquals(TelegramChannel.ID, result.id)
        assertEquals(0, result.children.size)
    }

    @Test
    fun `health - down`() {
        doReturn(ResponseEntity(mapOf("ok" to "---"), HttpStatus.OK))
            .whenever(rest)
            .getForObject(any<String>(), eq(Map::class.java))

        telegram.init(config, context)
        val result = telegram.health()

        assertEquals(false, result.up)
        assertEquals(TelegramChannel.ID, result.id)
        assertEquals(0, result.children.size)
    }

    @Test
    fun `health - error`() {
        doThrow(RuntimeException::class)
            .whenever(rest)
            .getForEntity(any<String>(), eq(Map::class.java))

        val result = telegram.health()

        assertEquals(false, result.up)
        assertEquals(TelegramChannel.ID, result.id)
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

    @Test
    fun `sendStatus - should send status message`() {
        telegram.init(config, context)
        doReturn("12345").whenever(users).get("user1")

        val message = Message(
            text = "🔧 Calling 2 tools",
            userId = "user1",
            channelId = TelegramChannel.ID,
            role = Role.SYSTEM,
        )
        telegram.sendStatus(message)

        verify(client).execute(any<SendMessage>())
    }

    @Test
    fun `sendStatus - should not send for wrong channel`() {
        telegram.init(config, context)

        val message = Message(
            text = "Status",
            userId = "user1",
            channelId = "other-channel",
            role = Role.SYSTEM,
        )
        telegram.sendStatus(message)

        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `sendStatus - should not send for unknown user`() {
        telegram.init(config, context)
        doReturn(null).whenever(users).get("unknown-user")

        val message = Message(
            text = "Status",
            userId = "unknown-user",
            channelId = TelegramChannel.ID,
            role = Role.SYSTEM,
        )
        telegram.sendStatus(message)

        verify(client, never()).execute(any<SendMessage>())
    }

    @Test
    fun `sendStatus - should handle errors gracefully`() {
        telegram.init(config, context)
        doReturn("12345").whenever(users).get("user1")
        doThrow(RuntimeException("Failed")).whenever(client).execute(any<SendMessage>())

        val message = Message(
            text = "Status",
            userId = "user1",
            channelId = TelegramChannel.ID,
            role = Role.SYSTEM,
        )
        telegram.sendStatus(message) // Should not throw
    }
}
