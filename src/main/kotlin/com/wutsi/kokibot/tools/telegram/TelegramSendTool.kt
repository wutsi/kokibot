package com.wutsi.kokibot.tools.telegram

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.channel.telegram.TelegramChannel
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class TelegramSendTool : Tool {
    companion object {
        const val ID = "telegram_send"
    }

    private lateinit var context: Context

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata {
        return ToolMetadata(
            name = "telegram_send",
            description = "Send a message to a user via Telegram chat. The message th send include a text and/or a file (document or photo)",
            parameters = listOf(
                ToolParameter(
                    name = "user_id",
                    type = ToolParameterType.STRING,
                    description = "Identifier of the recipient of the message",
                    required = true,
                ),
                ToolParameter(
                    name = "text",
                    type = ToolParameterType.STRING,
                    description = "The text of the message to send",
                    required = false
                ),
                ToolParameter(
                    name = "file",
                    type = ToolParameterType.STRING,
                    description = "Path of the file to send",
                    required = false
                )
            )
        )
    }

    override fun activate(): Boolean {
        return getChannel() != null
    }

    override fun exec(arguments: Map<*, *>): String {
        val userId = arguments["user_id"]?.toString()
            ?: throw IllegalArgumentException("Missing required parameter: user_id")
        val text = arguments["text"]?.toString()
        val file = arguments["file"]?.toString()
        val channel = getChannel() ?: return "Cannot send message via telegram. This channel is not available"

        try {
            channel.send(
                Message(
                    userId = userId,
                    channelId = channel.id(),
                    text = text ?: "",
                    filePaths = file?.ifEmpty { null }?.let { f -> listOf(f) } ?: emptyList()
                )
            )
            return "Message successfully sent to $userId via Telegram"
        } catch (ex: Exception) {
            return "Failed to send message to $userId. Error: ${ex.message}"
        }
    }

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val recipients = toolCalls.mapNotNull { call -> call.arguments["user_id"]?.toString() }
            .joinToString(",")
        return "Sending message to $recipients via Telegram"
    }

    private fun getChannel(): TelegramChannel? {
        return context.channelRegistry.all()
            .find { channel -> channel is TelegramChannel }
            as? TelegramChannel
    }
}
