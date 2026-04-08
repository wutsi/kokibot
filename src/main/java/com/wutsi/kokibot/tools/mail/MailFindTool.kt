package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Folder
import jakarta.mail.search.BodyTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.SubjectTerm
import kotlin.math.min

class MailFindTool : AbstractIMAPTool() {
    companion object {
        const val NAME = "mail_find"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Search emails by a keyword in the subject or body",
        parameters = listOf(
            ToolParameter(
                name = "keyword",
                description = "Keyword to search in the subject or body of the emails",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "limit",
                description = "Maximum number of emails to return. The default value is $LIMIT, and the max value is $MAX_LIMIT.",
                type = ToolParameterType.INTEGER,
                required = false
            ),
        )
    )

    override fun exec(arguments: Map<*, *>, inbox: Folder): String {
        val keyword = arguments["keyword"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: keyword")
        val limit = min(MAX_LIMIT, MapUtil.toInt("limit", arguments) ?: LIMIT)
        return find(keyword, limit, inbox)
    }

    private fun find(keyword: String, limit: Int, inbox: Folder): String {
        inbox.open(Folder.READ_ONLY)
        val term = OrTerm(
            SubjectTerm(keyword),
            BodyTerm(keyword)
        )
        val messages = inbox.search(term)
            .reversed()
            .take(limit)

        // Number of messages
        val sb = StringBuilder()
        sb.append("${messages.size} email(s) found with keyword '$keyword'\n")

        // Messages details
        sb.append(
            messages.joinToString("\n") { message ->
                "Email #${message.messageNumber}:\n" + toString(message)
            }
        )
        return sb.toString()
    }
}
