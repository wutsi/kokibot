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
        const val ID = "channel:websocket"
    }

    private lateinit var context: Context
    private lateinit var path: String
    private lateinit var handler: WebSocketHandler
    private val sessions = ConcurrentHashMap<String, WebSocketSession>() // userId -> session
    private val jsonMapper = JsonMapper()

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.path = config["path"]?.toString() ?: "/ws/${context.assistant.name}"

        // Create handler
        handler = WebSocketHandler(this)

        // Register with global WebSocket registry (static access)
        WebSocketChannelRegistry.registerChannel(this)

        LOGGER.info("Channel: websocket")
        LOGGER.info("  path: $path")
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

        // Unregister from global registry (static access)
        WebSocketChannelRegistry.unregisterChannel(this)
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
                type = WebSocketResponseType.TOOL_STATUS,
                content = message.text,
            )
            session.sendMessage(TextMessage(jsonMapper.writeValueAsString(response)))
        } catch (e: Exception) {
            LOGGER.warn("Error sending status to WebSocket: ${e.message}")
        }
    }

    internal fun handleMessage(session: WebSocketSession, payload: String) {
        try {
            val request = jsonMapper.readValue(payload, WebSocketRequest::class.java)
            val userId = request.userId ?: session.id

            // Register session for responses
            sessions[userId] = session

            // Create message
            val message = Message(
                text = request.query + "\n\n" +
                    """
                        When your refer to a file in the agent working directory (${context.home.absolutePath}/workspace), always format it as hyperlink in markdown format (instead of plain text or code format).
                        This allows the user to click and open the file directly from the message.

                        A message contains a file names `foo.pdf`, located in directory `${context.home.absolutePath}/workspace/a/b` convert it to
                         `[file.pdf](/files/${context.assistant.name}|workspace|a|b|foo.pdf)`.

                        For security reason, never hyperlink any file outside of the working directory.
                    """.trimIndent(),
                role = Role.USER,
                userId = userId,
                channelId = id(),
                filePaths = request.filePaths,
            )

            // Process with streaming callback (always enabled)
            val response = context.assistant.process(
                query = message,
                streamCallback = { delta ->
                    sendReasoningChunk(session, delta)
                },
            )

            // Send final response
            sendFinalResponse(session, response.text, userId)
        } catch (e: Exception) {
            LOGGER.error("Error processing WebSocket message", e)
            try {
                sendError(session, e.message ?: "Internal error")
            } catch (ex: Exception) {
                LOGGER.error("Error sending error '${e.message}' to WebSocket", ex)
            }
        }
    }

    internal fun handleConnectionEstablished(session: WebSocketSession) {
        LOGGER.info("WebSocket connection established: ${session.id}")
    }

    internal fun handleConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        LOGGER.info("WebSocket connection closed: ${session.id}, status: $status")
        sessions.values.removeIf { it.id == session.id }
    }

    private fun sendReasoningChunk(session: WebSocketSession, delta: String) {
        sendMessage(
            session,
            WebSocketResponse(
                type = WebSocketResponseType.REASONING_CHUNK,
                content = delta,
            ),
        )
    }

    private fun sendFinalResponse(session: WebSocketSession, content: String, userId: String) {
        sendMessage(
            session,
            WebSocketResponse(
                type = WebSocketResponseType.FINAL,
                content = content,
                finishReason = "DONE",
                contextLength = context.assistant.contextLength(
                    channelId = id(),
                    userId = userId
                ),
            ),
        )
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

    fun getHandler(): WebSocketHandler = handler
    fun getPath(): String = path

    internal fun getSession(userId: String): WebSocketSession? = sessions[userId]
}
