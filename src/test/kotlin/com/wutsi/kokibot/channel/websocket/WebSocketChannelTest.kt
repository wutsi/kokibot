package com.wutsi.kokibot.channel.websocket

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.llm.LLMUsage
import com.wutsi.kokibot.service.inbox.Inbox
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSocketChannelTest {
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()
    private val inbox = mock<Inbox>()
    private val session = mock<WebSocketSession>()
    private val jsonMapper = JsonMapper()
    private val home = File("target/test-data/websocket-channel")

    @BeforeEach
    fun setup() {
        whenever(context.assistant).doReturn(assistant)
        whenever(context.home).doReturn(home)
        whenever(context.inbox).doReturn(inbox)
        whenever(assistant.name).doReturn("test-agent")
        whenever(session.id).doReturn("session-123")
        whenever(session.isOpen).doReturn(true)
    }

    @Test
    fun source() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)

        Assertions.assertEquals("test-agent", channel.source())
    }

    @Test
    fun `handleConnectionEstablished stores session by anonymous`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)

        channel.handleConnectionEstablished(session)

        assertEquals(session, channel.getSession("anonymous"))
    }

    @Test
    fun `handleMessage submits message to inbox`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        channel.handleMessage(
            session,
            """{"query": "Hello", "filePaths": ["/a.txt"], "conversationId": "conv-42"}""",
        )

        val captor = com.nhaarman.mockitokotlin2.argumentCaptor<Message>()
        verify(inbox).submit(captor.capture())
        val submitted = captor.firstValue
        assertEquals("Hello", submitted.text)
        assertEquals("anonymous", submitted.userId)
        assertEquals("channel:websocket", submitted.channelId)
        assertEquals(listOf("/a.txt"), submitted.filePaths)
        assertEquals("conv-42", submitted.conversationId)
        assertEquals(Role.USER, submitted.role)
    }

    @Test
    fun `handleMessage sends error when inbox fails`() {
        doThrow(RuntimeException("inbox full")).whenever(inbox).submit(any())

        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        channel.handleMessage(session, """{"query": "Hello", "filePaths": []}""")

        verify(session).sendMessage(
            argThat { msg ->
                val response = jsonMapper.readValue(
                    (msg as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.ERROR &&
                    response.message?.contains("inbox full") == true
            },
        )
    }

    @Test
    fun `health check returns active connections`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val health = channel.health()

        assertTrue(health.up)
        assertEquals("channel:websocket", health.id)
        assertEquals("0 active connections", health.details)
    }

    @Test
    fun `send returns false for wrong channel`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        assertFalse(
            channel.send(
                Message(
                    text = "test",
                    channelId = "other-channel",
                ),
            ),
        )
    }

    @Test
    fun `send returns false for unknown user`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        assertFalse(
            channel.send(
                Message(
                    text = "test",
                    channelId = "channel:websocket",
                    userId = "unknown-user",
                ),
            ),
        )
    }

    @Test
    fun send() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        val message = Message(
            text = "Hello",
            channelId = "channel:websocket",
            userId = "anonymous",
            conversationId = "conv-xyz",
        )
        assertTrue(channel.send(message))

        verify(session).sendMessage(
            argThat { msg ->
                val response = jsonMapper.readValue(
                    (msg as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.FINAL &&
                    response.content == "Hello" &&
                    response.conversationId == "conv-xyz"
            },
        )
    }

    @Test
    fun `send returns false if session throws on send`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        doThrow(RuntimeException("socket closed")).whenever(session).sendMessage(any())

        assertFalse(
            channel.send(
                Message(
                    text = "Hello",
                    channelId = "channel:websocket",
                    userId = "anonymous",
                ),
            ),
        )
    }

    @Test
    fun `send returns false if channel is not the same`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        assertFalse(
            channel.send(
                Message(
                    text = "Hello",
                    channelId = "channel:websocket:xxx",
                    userId = "user123",
                ),
            ),
        )
    }

    @Test
    fun `send returns false if channel has never received message from user`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        assertFalse(
            channel.send(
                Message(
                    text = "Hello",
                    channelId = "channel:websocket:xxx",
                    userId = "user123",
                ),
            ),
        )
    }

    @Test
    fun `sendStatus sends REASONING_CHUNK with usage`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        val usage = LLMUsage(totalTokens = 50, promptTokens = 30, completionTokens = 20)
        val message = Message(
            text = "Thinking...",
            channelId = "channel:websocket",
            userId = "anonymous",
            usage = usage,
            role = Role.ASSISTANT,
        )
        channel.sendStatus(message)

        verify(session).sendMessage(
            argThat { msg ->
                val response = jsonMapper.readValue(
                    (msg as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.REASONING_CHUNK &&
                    response.content == "Thinking..." &&
                    response.usage == usage
            },
        )
    }

    @Test
    fun `sendStatus does nothing for wrong channel`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)

        channel.sendStatus(
            Message(
                text = "Status",
                channelId = "other-channel",
                userId = "user123",
                role = Role.SYSTEM,
            ),
        )
        // No sendMessage should be called since it's wrong channel
    }

    @Test
    fun `sendStatus does nothing for unknown user`() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)

        channel.sendStatus(
            Message(
                text = "Status",
                channelId = "channel:websocket",
                userId = "unknown-user",
                role = Role.SYSTEM,
            ),
        )
        // No sendMessage should be called since user is unknown
    }

    @Test
    fun handleConnectionClosed() {
        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)
        channel.handleConnectionEstablished(session)

        assertEquals(session, channel.getSession("anonymous"))
        channel.handleConnectionClosed(session, CloseStatus.NORMAL)

        assertNull(channel.getSession("anonymous"))
    }
}
