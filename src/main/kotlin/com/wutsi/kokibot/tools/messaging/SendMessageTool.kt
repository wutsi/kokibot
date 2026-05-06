package com.wutsi.kokibot.tools.messaging

import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class SendMessageTool : Tool {
    companion object {
        const val NAME = "send_message"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Send a message to the user via Telegram or other channels",
        parameters = listOf(
            ToolParameter(
                name = "user_id",
                description = "ID of the user to send the message to",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "channel_id",
                description = """
                    ID of the channel to use for sending email.
                    The channel supported are:
                       - `telegram`: For sending message to Telegram. The `user_id` should be the Telegram user ID of the recipient in this case.
                       - `email`: For sending email to the user. The `user_id` should be the email address of the recipient in this case.
                """.trimIndent(),
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
        } catch (_: ChannelNotFoundException) {
            return "Message was not sent. The channel $channelId is not available"
        } catch (ex: Exception) {
            return "Message was not sent to $userId via $channelId. Error=${ex.message}"
        }
    }

    private fun send(userId: String, channelId: String, message: String, filePaths: List<String>): String {
        val channel = context.channelRegistry.get("channel:$channelId")
        val result = channel.send(
            Message(
                userId = userId,
                channelId = "channel:$channelId",
                text = message,
                filePaths = filePaths,
            )
        )
        return if (result) {
            "Message sent to $userId via $channelId"
        } else {
            "Message not sent to $userId via $channelId"
        }
    }
}
