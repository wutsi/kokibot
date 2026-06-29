package com.wutsi.kokibot.channel.websocket

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

class WebSocketChannel : Channel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketChannel::class.java)
    }

    private lateinit var context: Context
    private val sessions = ConcurrentHashMap<String, WebSocketSession>() // userId -> session
    private val jsonMapper = JsonMapper()

    override fun name(): String = "websocket"

    override fun source(): String = context.assistant.name

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        WebSocketChannelRegistry.registerChannel(context.assistant.name, this)
        LOGGER.info("Channel: websocket (agent=${context.assistant.name})")
    }

    override fun destroy() {
        LOGGER.info("Closing ${sessions.size} WebSocket connections")
        sessions.values.forEach { session ->
            try {
                session.close(CloseStatus.GOING_AWAY)
            } catch (e: Exception) {
                LOGGER.warn("Error closing WebSocket session", e)
            }
        }
        sessions.clear()

        WebSocketChannelRegistry.unregisterChannel(context.assistant.name)
    }

    override fun health(): Health {
        return Health(
            id = id(),
            up = true,
            details = "${sessions.size} active connections",
        )
    }

    override fun send(message: Message): Boolean {
        if (message.channelId != id()) {
            return false
        }

        val session = sessions[message.userId] ?: return false

        try {
            val response = WebSocketResponse(
                type = WebSocketResponseType.FINAL,
                content = message.text,
                conversationId = message.conversationId,
            )
            session.sendMessage(TextMessage(jsonMapper.writeValueAsString(response)))
            return true
        } catch (e: Exception) {
            LOGGER.error("Error sending message to WebSocket", e)
            return false
        }
    }

    override fun sendStatus(message: Message) {
        if (message.channelId != id()) {
            return
        }

        val session = sessions[message.userId] ?: return

        try {
            val response = WebSocketResponse(
                type = WebSocketResponseType.REASONING_CHUNK,
                content = message.text,
                usage = message.usage,
            )
            session.sendMessage(TextMessage(jsonMapper.writeValueAsString(response)))
        } catch (e: Exception) {
            LOGGER.warn("Error sending status to WebSocket: ${e.message}")
        }
    }

    internal fun handleMessage(session: WebSocketSession, payload: String) {
        try {
            val request = jsonMapper.readValue(payload, WebSocketRequest::class.java)
            context.inbox.submit(
                Message(
                    text = request.query,
                    role = Role.USER,
                    userId = session.id,
                    channelId = id(),
                    filePaths = request.filePaths,
                    conversationId = request.conversationId,
                )
            )
        } catch (e: Exception) {
            LOGGER.error("Error submitting WebSocket message to inbox", e)
            try {
                sendError(session, e.message ?: "Internal error")
            } catch (ex: Exception) {
                LOGGER.error("Error sending error response to WebSocket", ex)
            }
        }
    }

    internal fun handleConnectionEstablished(session: WebSocketSession) {
        LOGGER.info("WebSocket connection established: ${session.id}")
        sessions[session.id] = session
    }

    internal fun handleConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        LOGGER.info("WebSocket connection closed: ${session.id}, status: $status")
        sessions.values.removeIf { it.id == session.id }
    }

    private fun sendError(session: WebSocketSession, errorMessage: String) {
        sendMessage(
            session,
            WebSocketResponse(
                type = WebSocketResponseType.ERROR,
                message = errorMessage,
            ),
        )
    }

    private fun sendMessage(session: WebSocketSession, response: WebSocketResponse) {
        if (session.isOpen()) {
            session.sendMessage(
                TextMessage(jsonMapper.writeValueAsString(response))
            )
        }
    }

    internal fun getSession(userId: String): WebSocketSession? = sessions[userId]
}
