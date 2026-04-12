package com.wutsi.kokibot.mail

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import java.util.Properties

/**
 * This is the SMTP service, which is used to send emails.
 */
class SMTP {
    private var from: String? = null
    private lateinit var host: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var port: String
    private lateinit var useTLS: String
    private lateinit var useSSL: String

    /**
     * Initialize the SMTP client with the given configuration and context.
     *
     * The configuration can contain the following parameters:
     * - host: the SMTP server host (required)
     * - port: the SMTP server port (required)
     * - username: the SMTP server username (required)
     * - password: the SMTP server password (required)
     * - from: the email address to use as the sender (required)
     * - use-ssl: whether to use SSL (default: false)
     * - use-tls: whether to use TLS (default: false)
     */
    fun init(config: Map<*, *>, context: Context) {
        host = config["host"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/host")

        username = config["username"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/username")

        password = config["password"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/password")

        port = config["port"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/port")

        from = config["from"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/smtp/from")

        useSSL = MapUtil.toString("use-ssl", config) ?: "false"
        useTLS = MapUtil.toString("use-tls", config) ?: "false"
    }

    fun destroy() {
    }

    fun getSession(): Session {
        val props = getProperties()
        return Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            }
        )
    }

    fun getFrom(): String? = from

    internal fun getProperties(): Properties {
        return Properties().apply {
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
    }
}
