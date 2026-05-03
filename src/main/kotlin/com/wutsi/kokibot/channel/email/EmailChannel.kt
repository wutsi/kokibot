package com.wutsi.kokibot.channel.email

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.HtmlUtil
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Authenticator
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.search.FlagTerm
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class EmailChannel(
    assistant: Assistant,
) : Channel(assistant) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EmailChannel::class.java)

        const val ID = "channel:email"
        const val DEFAULT_FREQUENCY_SECONDS = 15L * 60L // 15 minutes
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var context: Context
    private lateinit var email: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var imapHost: String
    private lateinit var imapPort: String
    private var imapSSL: String? = null
    private var imapTLS: String? = null
    private lateinit var smtpHost: String
    private lateinit var smtpPort: String
    private var smtpSSL: String? = null
    private var smtpTLS: String? = null
    private lateinit var senderWhitelist: List<String>
    private lateinit var job: ScheduledFuture<*>

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context

        email = config["email"] as? String
            ?: throw ConfigurationException("email is required")
        username = config["username"] as? String
            ?: throw ConfigurationException("username is required")
        password = config["password"] as? String
            ?: throw ConfigurationException("password is required")

        imapHost = config["imap-host"] as? String
            ?: throw ConfigurationException("imap-host is required")
        imapPort = (config["imap-port"] as? Int)?.toString() ?: "993"
        imapSSL = (config["imap-ssl"] as? Boolean)?.toString()
        imapTLS = (config["imap-tls"] as? Boolean)?.toString()

        smtpHost = config["smtp-host"] as? String
            ?: throw ConfigurationException("imap-host is required")
        smtpPort = (config["smtp-port"] as? Int)?.toString() ?: "465"
        smtpSSL = (config["smtp-ssl"] as? Boolean)?.toString()
        smtpTLS = (config["smtp-tls"] as? Boolean)?.toString()

        val frequency = (config["fetch-frequency"] as? String)
            ?.let { value -> DurationUtil.seconds(value, DEFAULT_FREQUENCY_SECONDS) }
            ?: DEFAULT_FREQUENCY_SECONDS

        senderWhitelist = MapUtil.toList("sender-whitelist", config)
            ?.map { entry -> entry.toString().lowercase() }
            ?: emptyList()

        job = launchJob(frequency)
    }

    override fun destroy() {
        job.cancel(false)
        scheduler.shutdown()
    }

    override fun send(message: Message): Boolean {
        try {
            send(message, null)
            return true
        } catch (e: Throwable) {
            LOGGER.warn("Failed to send message", e)
            return false
        }
    }

    override fun health(): Health {
        return try {
            // IMAP
            val session = getIMAPSession()
            val store = session.getStore(getIMAPProtocol())
            store.connect(imapHost, imapPort.toInt(), username, password)
            store.close()

            // SMTP
            getSMTPSession().transport.connect(smtpHost, smtpPort.toInt(), username, password)

            Health(id(), true)
        } catch (e: Exception) {
            Health(id(), false, e.message)
        }
    }

    private fun launchJob(frequency: Long): ScheduledFuture<*> {
        val channel = this
        val task = Runnable {
            LOGGER.info("Fetch emails...")
            channel.fetch()
        }

        val minutes = frequency / 60
        val seconds = frequency % 60
        LOGGER.info("Scheduling email fetch every ${minutes}m ${seconds}s")
        return scheduler.scheduleAtFixedRate(task, frequency, frequency, TimeUnit.SECONDS)
    }

    internal fun fetch() {
        try {
            val protocol = getIMAPProtocol()
            val session = getIMAPSession()
            val store = session.getStore(protocol)
            store.connect(imapHost, imapPort.toInt(), username, password)
            store.use {
                // 3. Open the Inbox
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_WRITE)
                inbox.use {
                    val unseenFlagTerm = FlagTerm(Flags(Flags.Flag.SEEN), false)
                    val messages = inbox.search(unseenFlagTerm)
                    var processed = 0
                    for (message in messages) {
                        if (!reject(message) && accept(message)) {
                            process(message)
                            message.setFlag(Flags.Flag.SEEN, true)
                            processed++
                        }
                    }
                    LOGGER.info("${messages.size} new messages, $processed processed")
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to send message", e)
        }
    }

    private fun getIMAPProtocol(): String {
        return if (imapSSL.toBoolean()) "imaps" else "imap"
    }

    private fun accept(message: jakarta.mail.Message): Boolean {
        val from = (message.from.firstOrNull() as? InternetAddress)?.address
        if (from != null && (senderWhitelist.isEmpty() || senderWhitelist.contains(from.lowercase()))) {
            return true
        } else {
            LOGGER.warn("Unauthorized sender: $from")
            return false
        }
    }

    private fun reject(message: jakarta.mail.Message): Boolean {
        val from = (message.from.firstOrNull() as? InternetAddress)?.address
            ?: return true

        return from.startsWith("noreply@") ||
            from.startsWith("no-reply@") ||
            from.startsWith("mailer-daemon@") ||
            from.startsWith("bounce@") ||
            from.contains(email)
    }

    private fun process(message: jakarta.mail.Message) {
        val prompt = Message(
            channelId = id(),
            role = Role.USER,
            userId = (message.from?.firstOrNull() as InternetAddress?)?.address,
            id = message.getHeader("Message-ID")?.firstOrNull() ?: UUID.randomUUID().toString(),
            subject = message.subject,
            text = extractBodyText(message),
            filePaths = extractAttachments(message).map { file -> file.absolutePath },
        )
        val result = assistant.process(
            prompt,
            {}
        )
        reply(prompt, result)
    }

    private fun reply(prompt: Message, result: Message) {
        send(
            message = Message(
                channelId = id(),
                role = Role.ASSISTANT,
                userId = prompt.userId,
                subject = "Re: " + (prompt.subject ?: "").removePrefix("Re: "),
                text = result.text,
            ),
            replyMessageId = prompt.id,
        )
    }

    private fun send(message: Message, replyMessageId: String?) {
        val session = getSMTPSession()
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(email))
            setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(message.userId))
            subject = message.subject
            setContent(createMultipartContent(message.text))
            if (replyMessageId != null) {
                setHeader("In-Reply-To", replyMessageId)
                setHeader("References", replyMessageId)
            }
        }

        Transport.send(message)
    }

    private fun createMultipartContent(markdown: String): Multipart {
        val html = HtmlUtil.fromMarkdown(markdown)
        val multipart = MimeMultipart()
        val body = MimeBodyPart()
        body.setContent(html, "text/html; charset=utf-8")
        body.setDisposition(Part.INLINE)
        multipart.addBodyPart(body)
        return multipart
    }

    private fun extractBodyText(message: jakarta.mail.Message): String {
        val content = message.content
        if (content is Multipart) {
            for (i in 0 until content.count) {
                val part = content.getBodyPart(i)
                if (part.isMimeType("text/plain")) {
                    return part.content.toString()
                } else if (part.isMimeType("text/html")) {
                    return HtmlUtil.toMarkdown(part.content.toString())
                }
            }
        }
        return content.toString()
    }

    private fun extractAttachments(message: jakarta.mail.Message): List<File> {
        val content = message.content
        val attachments = mutableListOf<File>()
        if (content is Multipart) {
            for (i in 0 until content.count) {
                val part = content.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) || part.fileName != null) {
                    if (part is MimeBodyPart) {
                        val file = context.fileService.createFile(part.fileName)
                        part.saveFile(file)
                        attachments.add(file)
                    }
                }
            }
        }
        return attachments
    }

    private fun getIMAPSession(): Session {
        val protocol = getIMAPProtocol()
        val props = Properties().apply {
            setProperty("mail.store.protocol", getIMAPProtocol())
            setProperty("mail.$protocol.host", imapHost)
            setProperty("mail.$protocol.port", imapPort)
            imapSSL?.let { setProperty("mail.$protocol.ssl.enable", imapSSL) }
            imapTLS?.let { setProperty("mail.$protocol.starttls.enable", imapTLS) }
        }
        return Session.getDefaultInstance(props)
    }

    private fun getSMTPSession(): Session {
        val props = Properties().apply {
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.host", smtpHost)
            setProperty("mail.smtp.port", smtpPort)
            smtpSSL?.let { setProperty("mail.smtp.ssl.enable", smtpSSL) }
            smtpTLS?.let { setProperty("mail.smtp.starttls.enable", smtpTLS) }
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })
    }
}
