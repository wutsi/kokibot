package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.HtmlUtil
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage

abstract class AbstractIMAPTool : Tool {
    companion object {
        const val LIMIT = 20
        const val MAX_LIMIT = 100
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun exec(arguments: Map<*, *>): String {
        val store = context.imap.getStore()
        store.use {
            val inbox = store.getFolder("INBOX")
            inbox.use {
                return exec(arguments, inbox)
            }
        }
    }

    abstract fun exec(arguments: Map<*, *>, inbox: Folder): String

    protected fun extractUnsubscribeUrl(message: Message): String? {
        // Headers can have multiple values, so we get an array
        val headers = message.getHeader("List-Unsubscribe") ?: return null

        // The header often looks like: <https://example.com/unsub>, <mailto:unsub@example.com>
        val rawHeader = headers.joinToString()

        // Use regex to find the first URL inside angle brackets < >
        val urlRegex = """<(https?://[^>]+)>""".toRegex()
        return urlRegex.find(rawHeader)?.groupValues?.get(1)
    }

    protected fun toString(message: Message, includeBody: Boolean = false): String {
        val unsubscribeUrl = extractUnsubscribeUrl(message) ?: "N/A"
        val messageId = if (message is MimeMessage) message.messageID else "N/A"

        val sb = StringBuilder()
        sb.append("- Date: ${message.receivedDate}\n")
        sb.append("- Message-ID: $messageId\n")
        sb.append("- Unsubscribe-URL: $unsubscribeUrl\n")
        sb.append("- From: ${message.from.joinToString(", ")}\n")
        sb.append("- Subject: ${message.subject}\n")

        if (includeBody) {
            sb.append("- Body:\n")
            sb.append(">>> BEGIN_BODY ...........\n" + toBody(message) + ">>> END BODY ...........\n\n")
        }
        return sb.toString()
    }

    private fun toBody(message: Part): String {
        return when {
            message.isMimeType("text/plain") -> message.content as String

            message.isMimeType("text/html") -> HtmlUtil.toMarkdown(message.content as String)

            message.isMimeType("multipart/*") -> {
                val multiPart = message.content as Multipart
                val result = StringBuilder()

                for (i in 0 until multiPart.count) {
                    val bodyPart = multiPart.getBodyPart(i)
                    val partText = toBody(bodyPart)
                    if (partText.isNotEmpty()) {
                        result.append(partText + "\n")
                    }
                }
                result.toString()
            }

            else -> ""
        }
    }
}
