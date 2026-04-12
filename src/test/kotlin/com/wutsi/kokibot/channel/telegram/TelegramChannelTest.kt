package com.wutsi.kokibot.channel.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.exception.ConfigurationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.exceptions.TelegramApiErrorResponseException
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.File
import kotlin.test.assertEquals

class TelegramChannelTest {
    private val app = mock<TelegramBotsLongPollingApplication>()
    private val client = mock<TelegramClient>()
    private val factory = mock<TelegramFactory>()
    private val agent = mock<Assistant>()
    private val telegram = TelegramChannel(agent, factory)
    private val config = mapOf("token" to "test-token")
    private val context = Context(
        home = File("target/test-data/telegram"),
        llm = mock()
    )

    @BeforeEach
    fun setUp() {
        doReturn(client).whenever(factory).createTelegramClient(any())
        doReturn(app).whenever(factory).createTelegramBotsLongPollingApplication()
    }

    @Test
    fun `init - should create telegram client and register bot`() {
        // WHEN
        telegram.init(config, context)

        // THEN
        verify(factory).createTelegramClient("test-token")
        verify(factory).createTelegramBotsLongPollingApplication()
        verify(app).registerBot(eq("test-token"), any())
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
        verify(app).unregisterBot("test-token")
    }

    @Test
    fun `destroy - should do nothing if not initialized`() {
        // WHEN
        telegram.destroy()

        // THEN
        verify(app, never()).unregisterBot(any())
    }

    @Test
    fun `consume - should forward message to agent and return response`() {
        // GIVEN
        telegram.init(config, context)
        doReturn(Message("World")).whenever(agent).process(any())

        // WHEN
        val update = createUpdateText("Hello", 123L)
        telegram.consume(update)

        // THEN
        val prompt = argumentCaptor<Message>()
        verify(agent).process(prompt.capture())
        assertEquals("Hello", prompt.firstValue.text)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals("ray.sponsible@telegram", prompt.firstValue.userId)

        val sendMessage = argumentCaptor<SendMessage>()
        verify(client).execute(sendMessage.capture())
        assertEquals("World", sendMessage.firstValue.text)
        assertEquals("123", sendMessage.firstValue.chatId)
    }

    @Test
    fun `consume - should ignore update without message text`() {
        // GIVEN
        telegram.init(config, context)

        // WHEN
        val update = createUpdateText(null, 4309)
        telegram.consume(update)

        // THEN
        verify(agent, never()).process(any())

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
        val update = createUpdateWithNoMessage(555)
        telegram.consume(update)

        // THEN
        verify(agent, never()).process(any())
        verify(client, never()).execute(any<SendMessage>())
    }

    private fun createUpdateText(text: String?, chatId: Long): Update {
        val chat = Chat(chatId, "")
        chat.userName = "ray.sponsible"
        chat.firstName = "Ray"
        chat.lastName = "Responsible"

        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = chat
        message.text = text
        update.message = message
        return update
    }

    private fun createUpdateWithNoMessage(chatId: Long): Update {
        val update = Update()
        val message = org.telegram.telegrambots.meta.api.objects.message.Message()
        message.chat = Chat(chatId, "")
        return update
    }
}
