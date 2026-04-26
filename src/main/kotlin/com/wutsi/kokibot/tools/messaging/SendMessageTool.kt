package com.wutsi.kokibot.tools.messaging

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.slf4j.LoggerFactory

class SendMessageTool : Tool {
    companion object {
        const val NAME = "send_message"
        private val LOGGER = LoggerFactory.getLogger(SendMessageTool::class.java)
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Send a message to the user via Telegram or other channels.
            You can specify the channel to use for sending the message.
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "user_id",
                description = "ID of the user to send the message to",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "channel_id",
                description = "ID of the channel to user for sending the message: Ex: channel:telegram",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "message",
                description = "Message to send",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "file_paths",
                description = "Path of the files to send, separated by comma (optional)",
                type = ToolParameterType.STRING,
                required = false
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val userId = arguments["user_id"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: user_id")

        val channelId = arguments["channel_id"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: channel_Id")

        val message = arguments["message"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: message")

        val filePaths = arguments["file_paths"]?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()

        try {
            return send(userId, channelId, message, filePaths)
        } catch (ex: Exception) {
            LOGGER.warn("Unexpected error sending message to user $userId via channel $channelId: $message", ex)
            return "Message was not sent to user $userId via $channelId. Error=${ex.message}"
        }
    }

    private fun send(userId: String, channelId: String, message: String, filePaths: List<String>): String {
        val channel = context.channelRegistry.get(channelId)
        channel.send(
            Message(
                userId = userId,
                channelId = channelId,
                text = message,
                filePaths = filePaths,
            )
        )
        return "Message sent to user $userId via channel $channelId"
    }
}
