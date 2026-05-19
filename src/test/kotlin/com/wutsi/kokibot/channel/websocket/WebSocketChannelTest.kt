package com.wutsi.kokibot.channel.websocket

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.Test
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSocketChannelTest {
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()
    private val session = mock<WebSocketSession>()
    private val jsonMapper = JsonMapper()

    @Test
    fun `valid request returns final response`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")
        whenever(assistant.process(any<Message>(), any())).doReturn(
            Message(
                text = "Hello, world!",
                role = Role.ASSISTANT,
            ),
        )
        whenever(session.id).doReturn("session-123")

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)
        try {
            channel.handleMessage(
                session,
                """
            {"query": "Hello", "userId": "user123"}
            """.trimIndent(),
            )

            verify(session).sendMessage(
                argThat { message ->
                    val response = jsonMapper.readValue(
                        (message as TextMessage).payload,
                        WebSocketResponse::class.java,
                    )
                    response.type == WebSocketResponseType.FINAL &&
                        response.content == "Hello, world!"
                },
            )
        } finally {
            channel.destroy()
        }
    }

    @Test
    fun `streaming sends multiple chunks`() {
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")
        whenever(session.id).doReturn("session-123")

        doAnswer { invocation ->
            val callback = invocation.getArgument<(String) -> Unit>(1)
            callback("Thinking...")
            callback("Analyzing...")
            Message(text = "Final answer", role = Role.ASSISTANT)
        }.whenever(assistant).process(any(), any())

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        channel.handleMessage(
            session,
            """
            {"query": "Complex question", "userId": "user123"}
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
        whenever(context.assistant).doReturn(assistant)
        whenever(assistant.name).doReturn("test-agent")
        whenever(session.id).doReturn("session-123")

        doThrow(RuntimeException("Failed")).whenever(assistant).process(any(), any())

        val channel = WebSocketChannel()
        channel.init(mapOf("path" to "/ws/test"), context)

        channel.handleMessage(
            session,
            """
            {"query": "Complex question", "userId": "user123"}
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
        assertEquals("channel:websocket:test-agent", health.id)
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
}
