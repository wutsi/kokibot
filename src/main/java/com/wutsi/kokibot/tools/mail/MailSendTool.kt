package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

class MailSendTool : Tool {
    companion object {
        const val NAME = "mail_send"
    }

    private lateinit var host: String
    private lateinit var from: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var port: String
    private lateinit var useTLS: String
    private lateinit var useSSL: String

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Send email to a recipient, or reply to an email",
        parameters = listOf(
            ToolParameter(
                name = "from_name",
                description = "Name of the sender to display in the email. This field is optional. If not provided, the email will be sent without a sender name",
                type = ToolParameterType.STRING,
                required = false
            ),
            ToolParameter(
                name = "to",
                description = "Email address of the recipient to send the email to. For replying to an email, this field should be the email address of the original sender",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "subject",
                description = "Subject of the email to send. For replying to an email, this field should be the subject of the original email prefixed with 'Re: '",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "body",
                description = "Body of the email to send",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "reply_message_id",
                description = "Message-ID of the message to reply to. If not provided, the email will be sent as a new email",
                type = ToolParameterType.STRING,
                required = false
            ),
        )
    )

    override fun init(config: Map<*, *>, context: Context) {
        val mail = MapUtil.toMap("mail", context.config)
            ?: throw ConfigurationException("Missing required configuration: mail")

        val smtp = MapUtil.toMap("smtp", mail)
            ?: throw ConfigurationException("Missing required configuration: mail/smtp")

        host = smtp["host"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/host")

        username = smtp["username"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/username")

        password = smtp["password"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/password")

        port = smtp["port"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/port")

        from = smtp["from"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/from")

        useSSL = MapUtil.toString("use-ssl", smtp) ?: "false"
        useTLS = MapUtil.toString("use-tls", smtp) ?: "false"
    }

    override fun exec(arguments: Map<*, *>): String {
        val fromName = arguments["from_name"]?.toString()

        val to = arguments["to"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: to")

        val subject = arguments["subject"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: subject")

        val body = arguments["body"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: body")

        val replyMessageId = arguments["reply_message_id"]?.toString()?.ifEmpty { null }

        return send(fromName, to, subject, body, replyMessageId)
    }

    private fun send(
        fromName: String?,
        to: String,
        subject: String,
        body: String,
        replyMessageId: String?
    ): String {
        val session = getSession()
        val senderEmail = from
        val message = MimeMessage(session).apply {
            setFrom(toInternetAddress(senderEmail, fromName))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject)
            setText(body)
            if (replyMessageId != null) {
                setHeader("In-Reply-To", replyMessageId)
                setHeader("References", replyMessageId)
            }
        }

        Transport.send(message)
        return "SUCCESS - Email sent to $to"
    }

    private fun toInternetAddress(email: String, name: String?): InternetAddress {
        return if (name.isNullOrEmpty()) {
            InternetAddress(email)
        } else {
            InternetAddress(email, name)
        }
    }

    private fun getSession(): Session {
        val props = Properties().apply {
            if (useSSL.toBoolean()) {
                put("mail.smtp.ssl.enable", "true")
            }
            if (useTLS.toBoolean()) {
                put("mail.smtp.starttls.enable", "true")
            }
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
        }

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        })
    }
}
