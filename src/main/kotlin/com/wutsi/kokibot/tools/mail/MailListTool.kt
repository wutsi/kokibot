package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.search.FlagTerm
import kotlin.math.min

class MailListTool : AbstractIMAPTool() {
    companion object {
        const val NAME = "mail_ls"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "List the emails in the inbox.",
        parameters = listOf(
            ToolParameter(
                name = "unread",
                description = "Unread emails only",
                type = ToolParameterType.BOOLEAN,
                required = false
            ),
            ToolParameter(
                name = "earliest",
                description = "Specifies the earliest time in the emails. The accepted values: 1d (1 day earlier), 2h (2 hours earlier), 30m (30m earlier)",
                type = ToolParameterType.STRING,
                required = false
            ),
            ToolParameter(
                name = "limit",
                description = "Maximum number of emails to return. The default value is $LIMIT and the max value is $MAX_LIMIT.",
                type = ToolParameterType.INTEGER,
                required = false
            ),
        )
    )

    override fun exec(arguments: Map<*, *>, inbox: Folder): String {
        val unread = arguments["unread"]
        val earliest = arguments["earliest"]?.toString()?.ifEmpty { null }
        val limit = min(MAX_LIMIT, MapUtil.toInt("limit", arguments) ?: LIMIT)
        return list(unread == true, earliest, limit, inbox)
    }

    private fun list(unread: Boolean?, earliest: String?, limit: Int, inbox: Folder): String {
        inbox.open(Folder.READ_ONLY)
        val minDate = earliest?.let { System.currentTimeMillis() - earliestValue(earliest) }
        val messages = if (unread == true) {
            inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
        } else {
            inbox.messages
        }.reversed()
        val xmessages = filter(messages, minDate, limit)

        // Number of messages
        val sb = StringBuilder()
        if (unread == true) {
            sb.append("${xmessages.size} unread email(s) found")
        } else {
            sb.append("${xmessages.size} email(s) found")
        }
        if (minDate != null) {
            sb.append(" since $minDate\n")
        } else {
            sb.append("\n")
        }

        // Messages details
        sb.append(
            xmessages.joinToString("\n") { message ->
                "Email #${message.messageNumber}:\n" + toString(message)
            }
        )
        return sb.toString()
    }

    internal fun filter(messages: List<Message>, earliest: Long?, limit: Int): List<Message> {
        var i = 0
        val xmessages = mutableListOf<Message>()
        if (earliest != null) {
            while (i < messages.size) {
                val msg = messages[i++]
                if (msg.receivedDate.time >= earliest) {
                    xmessages.add(msg)
                    if (xmessages.size >= limit) {
                        break
                    }
                } else {
                    break
                }
            }
        } else {
            xmessages.addAll(messages.take(limit))
        }
        return xmessages
    }

    internal fun earliestValue(earliest: String): Long {
        return when {
            earliest.endsWith("d") -> {
                val days = earliest.dropLast(1).toIntOrNull() ?: 1
                days * 24 * 60 * 60 * 1000L
            }

            earliest.endsWith("h") -> {
                val hours = earliest.dropLast(1).toIntOrNull() ?: 1
                hours * 60 * 60 * 1000L
            }

            earliest.endsWith("m") -> {
                val minutes = earliest.dropLast(1).toIntOrNull() ?: 1
                minutes * 60 * 1000L
            }

            else -> 24 * 60 * 60 * 1000L // default to 1 day
        }
    }
}
