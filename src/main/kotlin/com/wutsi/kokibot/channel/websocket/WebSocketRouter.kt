package com.wutsi.kokibot.channel.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketRouter : TextWebSocketHandler() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketRouter::class.java)
    }

    private val sessionChannels = ConcurrentHashMap<String, WebSocketChannel>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val agentName = extractAgentName(session)
        val channel = agentName?.let { WebSocketChannelRegistry.findChannel(it) }

        if (channel == null) {
            LOGGER.warn("No WebSocket channel found for agent: $agentName")
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }

        sessionChannels[session.id] = channel
        channel.handleConnectionEstablished(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val channel = sessionChannels[session.id]
        if (channel == null) {
            LOGGER.warn("No channel found for session: ${session.id}")
            session.close(CloseStatus.SESSION_NOT_RELIABLE)
            return
        }
        channel.handleMessage(session, message.payload)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val channel = sessionChannels.remove(session.id)
        channel?.handleConnectionClosed(session, status)
    }

    private fun extractAgentName(session: WebSocketSession): String? {
        val query = session.uri?.query ?: return null
        return query.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == "agent" }
            ?.get(1)
    }
}
