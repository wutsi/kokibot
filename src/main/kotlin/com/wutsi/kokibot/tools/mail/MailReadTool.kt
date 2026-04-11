package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import jakarta.mail.Folder
import jakarta.mail.search.MessageIDTerm

class MailReadTool : AbstractIMAPTool() {
    companion object {
        const val NAME = "mail_read"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Read the content of an email in the INBOX",
        parameters = listOf(
            ToolParameter(
                name = "message_id",
                description = "ID of the email to read, which includes the angle brackets (`<` and `>`). Example: <1415624532.2.1775510781531@127.0.0.1>",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>, inbox: Folder): String {
        val messageId = arguments["message_id"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: message_id")

        return read(messageId, inbox)
    }

    private fun read(messageId: String, inbox: Folder): String {
        inbox.open(Folder.READ_ONLY)
        val message = inbox.search(MessageIDTerm(messageId)).firstOrNull()
            ?: return "Email $messageId not found"
        return "Here are the details of the email $messageId:\n" + toString(message, true)
    }
}
