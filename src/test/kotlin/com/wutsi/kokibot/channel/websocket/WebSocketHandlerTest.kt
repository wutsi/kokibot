package com.wutsi.kokibot.channel.websocket

import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketHandlerTest {
    private val channel = mock<WebSocketChannel>()
    private val handler = WebSocketHandler(channel)
    private val session = mock<WebSocketSession>()

    @Test
    fun afterConnectionEstablished() {
        handler.afterConnectionEstablished(session)

        verify(channel).handleConnectionEstablished(session)
    }

    @Test
    fun handleTextMessage() {
        val message = TextMessage("Yo man")
        handler.handleMessage(session, message)

        verify(channel).handleMessage(session, "Yo man")
    }

    @Test
    fun afterConnectionClosed() {
        val status = mock<org.springframework.web.socket.CloseStatus>()
        handler.afterConnectionClosed(session, status)

        verify(channel).handleConnectionClosed(session, status)
    }
}
