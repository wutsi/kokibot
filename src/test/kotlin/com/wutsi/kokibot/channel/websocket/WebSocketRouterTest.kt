package com.wutsi.kokibot.channel.websocket

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI

class WebSocketRouterTest {
    private val router = WebSocketRouter()
    private val session = mock<WebSocketSession>()
    private val channel = mock<WebSocketChannel>()

    @BeforeEach
    fun setup() {
        whenever(session.id).doReturn("session-123")
        whenever(session.uri).doReturn(URI("ws://localhost/ws?agent=test-agent"))
        WebSocketChannelRegistry.registerChannel("test-agent", channel)
    }

    @AfterEach
    fun teardown() {
        WebSocketChannelRegistry.unregisterChannel("test-agent")
    }

    @Test
    fun afterConnectionEstablished() {
        router.afterConnectionEstablished(session)

        verify(channel).handleConnectionEstablished(session)
    }

    @Test
    fun handleTextMessage() {
        router.afterConnectionEstablished(session)
        router.handleMessage(session, TextMessage("Hello"))

        verify(channel).handleMessage(session, "Hello")
    }

    @Test
    fun afterConnectionClosed() {
        router.afterConnectionEstablished(session)
        val status = CloseStatus.NORMAL
        router.afterConnectionClosed(session, status)

        verify(channel).handleConnectionClosed(session, status)
    }

    @Test
    fun `closes session when agent not found`() {
        whenever(session.uri).doReturn(URI("ws://localhost/ws?agent=unknown"))
        router.afterConnectionEstablished(session)

        verify(session).close(CloseStatus.NOT_ACCEPTABLE)
    }

    @Test
    fun `closes session when no agent param`() {
        whenever(session.uri).doReturn(URI("ws://localhost/ws"))
        router.afterConnectionEstablished(session)

        verify(session).close(CloseStatus.NOT_ACCEPTABLE)
    }
}
