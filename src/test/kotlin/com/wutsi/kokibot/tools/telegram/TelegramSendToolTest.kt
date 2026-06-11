package com.wutsi.kokibot.tools.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramSendToolTest {
    private val telegramChannel = mock<TelegramChannel>()
    private val channelRegistry = mock<ChannelRegistry>()
    private val context = mock<Context>()
    private val tool = TelegramSendTool()

    @BeforeEach
    fun setUp() {
        doReturn(channelRegistry).whenever(context).channelRegistry
        doReturn(listOf(telegramChannel)).whenever(channelRegistry).all()
        tool.init(emptyMap<String, String>(), context)
    }

    @Test
    fun id() {
        assertEquals(TelegramSendTool.ID, tool.id())
        assertEquals("telegram_send", tool.id())
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()

        assertEquals("telegram_send", meta.name)
        assertTrue(meta.description.isNotBlank())
        assertEquals(3, meta.parameters.size)

        val userId = meta.parameters[0]
        assertEquals("user_id", userId.name)
        assertEquals(ToolParameterType.STRING, userId.type)
        assertTrue(userId.required)
        assertTrue(userId.description.isNotBlank())

        val text = meta.parameters[1]
        assertEquals("text", text.name)
        assertEquals(ToolParameterType.STRING, text.type)
        assertFalse(text.required)
        assertTrue(text.description.isNotBlank())

        val file = meta.parameters[2]
        assertEquals("file", file.name)
        assertEquals(ToolParameterType.STRING, file.type)
        assertFalse(file.required)
        assertTrue(file.description.isNotBlank())
    }

    @Test
    fun activate() {
        assertTrue(tool.activate())
    }

    @Test
    fun `activate - no telegram channel`() {
        doReturn(emptyList<Any>()).whenever(channelRegistry).all()
        assertFalse(tool.activate())
    }

    @Test
    fun exec() {
        val result = tool.exec(mapOf("user_id" to "user123", "text" to "Hello World"))

        assertEquals("Message successfully sent to user123 via Telegram", result)

        val messageCaptor = argumentCaptor<Message>()
        verify(telegramChannel).send(messageCaptor.capture())
        assertEquals("user123", messageCaptor.firstValue.userId)
        assertEquals("Hello World", messageCaptor.firstValue.text)
        assertTrue(messageCaptor.firstValue.filePaths.isEmpty())
    }

    @Test
    fun `exec - with file`() {
        val result = tool.exec(
            mapOf(
                "user_id" to "user123",
                "text" to "See attached",
                "file" to "/path/to/document.pdf"
            )
        )

        assertEquals("Message successfully sent to user123 via Telegram", result)

        val messageCaptor = argumentCaptor<Message>()
        verify(telegramChannel).send(messageCaptor.capture())
        assertEquals("user123", messageCaptor.firstValue.userId)
        assertEquals("See attached", messageCaptor.firstValue.text)
        assertEquals(listOf("/path/to/document.pdf"), messageCaptor.firstValue.filePaths)
    }

    @Test
    fun `exec - text only no file`() {
        val result = tool.exec(mapOf("user_id" to "user123", "text" to "Hello"))

        assertEquals("Message successfully sent to user123 via Telegram", result)

        val messageCaptor = argumentCaptor<Message>()
        verify(telegramChannel).send(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.filePaths.isEmpty())
    }

    @Test
    fun `exec - empty file path is ignored`() {
        val result = tool.exec(mapOf("user_id" to "user123", "text" to "Hello", "file" to ""))

        assertEquals("Message successfully sent to user123 via Telegram", result)

        val messageCaptor = argumentCaptor<Message>()
        verify(telegramChannel).send(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.filePaths.isEmpty())
    }

    @Test
    fun `exec - missing text sends empty string`() {
        val result = tool.exec(mapOf("user_id" to "user123"))

        assertEquals("Message successfully sent to user123 via Telegram", result)

        val messageCaptor = argumentCaptor<Message>()
        verify(telegramChannel).send(messageCaptor.capture())
        assertEquals("", messageCaptor.firstValue.text)
    }

    @Test
    fun `exec - missing user_id throws`() {
        val ex = assertThrows<IllegalArgumentException> {
            tool.exec(emptyMap<String, Any>())
        }
        assertTrue(ex.message?.contains("user_id") == true)
    }

    @Test
    fun `exec - channel exception returns error message`() {
        doThrow(RuntimeException("Network error")).whenever(telegramChannel).send(any())

        val result = tool.exec(mapOf("user_id" to "user123", "text" to "Hello"))

        assertTrue(result.startsWith("Failed to send message to user123"))
        assertTrue(result.contains("Network error"))
    }

    @Test
    fun `exec - no telegram channel`() {
        doReturn(emptyList<Any>()).whenever(channelRegistry).all()

        val result = tool.exec(mapOf("user_id" to "user123", "text" to "Hello"))

        assertEquals("Cannot send message via telegram. This channel is not available", result)
        verify(telegramChannel, never()).send(any())
    }

    @Test
    fun statusText() {
        val toolCalls = listOf(
            LLMToolCall(name = TelegramSendTool.ID, arguments = mapOf("user_id" to "user1")),
            LLMToolCall(name = TelegramSendTool.ID, arguments = mapOf("user_id" to "user2")),
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Sending message to user1,user2 via Telegram", result)
    }

    @Test
    fun `statusText - single recipient`() {
        val toolCalls = listOf(
            LLMToolCall(name = TelegramSendTool.ID, arguments = mapOf("user_id" to "user1")),
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Sending message to user1 via Telegram", result)
    }

    @Test
    fun `statusText - no tool calls`() {
        val result = tool.statusText(emptyList())
        assertEquals("Sending message to  via Telegram", result)
    }
}
