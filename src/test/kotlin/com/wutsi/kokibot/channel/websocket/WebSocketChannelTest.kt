package com.wutsi.kokibot.channel.websocket

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.websocket.WebSocketChannel.Companion.ANONYMOUS_USER
import com.wutsi.kokibot.llm.LLMStreamData
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
    private val session = mock<WebSocketSession>()
    private val jsonMapper = JsonMapper()
    private val home = File("target/test-data/websocket-channel")

    @BeforeEach
    fun setup() {
        whenever(context.assistant).doReturn(assistant)
        whenever(context.home).doReturn(home)
        whenever(assistant.name).doReturn("test-agent")
        whenever(session.id).doReturn("session-123")
        whenever(session.isOpen).doReturn(true)

        val msg = Message(text = "Final answer", role = Role.ASSISTANT, conversationId = "conv-test-123")
        doReturn(msg).whenever(assistant).process(any(), any())
    }

    @Test
    fun `valid request returns final response`() {
        whenever(assistant.process(any<Message>(), any())).doReturn(
            Message(
                text = "Hello, world!",
                role = Role.ASSISTANT,
                userId = ANONYMOUS_USER,
                conversationId = "conv-test-123",
            ),
        )

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        try {
            channel.handleMessage(
                session,
                """
                {
                    "query": "Hello",
                    "filePaths": ["/path/to/file1.txt", "/path/to/file2.txt"]
                }
                """.trimIndent(),
            )

            assertEquals(session, channel.getSession(ANONYMOUS_USER))

            verify(session).sendMessage(
                argThat { message ->
                    val response = jsonMapper.readValue(
                        (message as TextMessage).payload,
                        WebSocketResponse::class.java,
                    )
                    response.type == WebSocketResponseType.FINAL &&
                        response.content == "Hello, world!" &&
                        response.conversationId == "conv-test-123"
                },
            )
        } finally {
            channel.destroy()
        }
    }

    @Test
    fun `streaming sends multiple chunks`() {
        doAnswer { invocation ->
            val callback = invocation.getArgument<(LLMStreamData) -> Unit>(1)
            callback(LLMStreamData(text = "Thinking..."))
            callback(LLMStreamData(text = "Analyzing..."))
            Message(text = "Final answer", role = Role.ASSISTANT)
        }.whenever(assistant).process(any(), any())

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        channel.handleMessage(
            session,
            """
            {"query": "Complex question", "filePaths": []}
            """.trimIndent(),
        )

        // Verify reasoning chunks sent
        verify(session).sendMessage(
            argThat { message ->
                val response = jsonMapper.readValue(
                    (message as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.REASONING_CHUNK &&
                    response.content == "Thinking..."
            },
        )

        verify(session).sendMessage(
            argThat { message ->
                val response = jsonMapper.readValue(
                    (message as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.REASONING_CHUNK &&
                    response.content == "Analyzing..."
            },
        )

        // Verify final response sent
        verify(session).sendMessage(
            argThat { message ->
                val response = jsonMapper.readValue(
                    (message as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.FINAL &&
                    response.content == "Final answer"
            },
        )
    }

    @Test
    fun `backend issue sends error response`() {
        doThrow(RuntimeException("Failed")).whenever(assistant).process(any(), any())

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        channel.handleMessage(
            session,
            """
            {"query": "Complex question", "filePaths": []}
            """.trimIndent(),
        )

        verify(session).sendMessage(
            argThat { message ->
                val response = jsonMapper.readValue(
                    (message as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.ERROR &&
                    response.message?.contains("Failed") == true
            },
        )
    }

    @Test
    fun `health check returns active connections`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val health = channel.health()

        assertTrue(health.up)
        assertEquals("channel:websocket", health.id)
        assertEquals("0 active connections", health.details)
    }

    @Test
    fun `send returns false for wrong channel`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "test",
            channelId = "other-channel",
        )

        assertFalse(channel.send(message))
    }

    @Test
    fun `send returns false for unknown user`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "test",
            channelId = "channel:websocket:test-agent",
            userId = "unknown-user",
        )

        assertFalse(channel.send(message))
    }

    @Test
    fun `getPath returns configured path`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/custom"), context)

        assertEquals("/ws/custom", channel.getPath())
    }

    @Test
    fun `getPath uses default when not configured`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")

        val channel = WebSocketChannel()
        channel.init(emptyMap<String, Any>(), context)

        assertEquals("/ws/test-agent", channel.getPath())
    }

    @Test
    fun send() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")
        whenever(session.id).doReturn("session-123")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "Hello",
            channelId = "channel:websocket",
            userId = ANONYMOUS_USER,
            filePaths = emptyList(),
        )
        channel.handleMessage(session, """{"query": "Hello", "filePaths": []}""")
        assertTrue(channel.send(message))

        verify(session).sendMessage(
            argThat { msg ->
                val response = jsonMapper.readValue(
                    (msg as TextMessage).payload,
                    WebSocketResponse::class.java,
                )
                response.type == WebSocketResponseType.FINAL &&
                    response.content == "Hello"
            },
        )
    }

    @Test
    fun `send returns false if session failed`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        channel.handleMessage(session, """{"query": "Hello", "filePaths": []}""")

        doThrow(RuntimeException("Failed")).whenever(session).sendMessage(any())
        val message = Message(
            text = "Hello",
            channelId = "channel:websocket:test-agent",
            userId = "user123",
        )
        assertFalse(channel.send(message))
    }

    @Test
    fun `send returns false if channel is not the same`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        channel.handleMessage(session, """{"query": "Hello"}""")

        val message = Message(
            text = "Hello",
            channelId = "channel:websocket:xxx",
            userId = "user123",
        )
        assertFalse(channel.send(message))
    }

    @Test
    fun `send returns false if channel has never received message from user`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "Hello",
            channelId = "channel:websocket:xxx",
            userId = "user123",
        )
        assertFalse(channel.send(message))
    }

    @Test
    fun handleConnectionEstablished() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        channel.handleConnectionEstablished(session)
    }

    @Test
    fun handleConnectionClosed() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        channel.init(mapOf("path" to "/ws/test"), context)
        channel.handleMessage(session, """{"query": "Hello"}""")

        channel.handleConnectionClosed(session, CloseStatus.NORMAL)

        assertNull(channel.getSession("user123"))
    }

    @Test
    fun `sendStatus sends tool status message`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        channel.handleMessage(session, """{"query": "Hello", "filePaths": []}""")
        reset(session)

        val message = Message(
            text = "🔧 Calling 2 tools: web_search, file_read",
            channelId = "channel:websocket",
            userId = ANONYMOUS_USER,
            role = Role.SYSTEM,
        )
        channel.sendStatus(message)

        verify(session).sendMessage(argThat { msg ->
            val response = jsonMapper.readValue(
                (msg as TextMessage).payload,
                WebSocketResponse::class.java,
            )
            response.type == WebSocketResponseType.TOOL_STATUS &&
                response.content == "🔧 Calling 2 tools: web_search, file_read"
        })
    }

    @Test
    fun `sendStatus does nothing for wrong channel`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "Status",
            channelId = "other-channel",
            userId = "user123",
            role = Role.SYSTEM,
        )
        channel.sendStatus(message)

        // No sendMessage should be called since it's wrong channel
    }

    @Test
    fun `sendStatus does nothing for unknown user`() {
        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        val message = Message(
            text = "Status",
            channelId = "channel:websocket:test-agent",
            userId = "unknown-user",
            role = Role.SYSTEM,
        )
        channel.sendStatus(message)

        // No sendMessage should be called since user is unknown
    }
}
