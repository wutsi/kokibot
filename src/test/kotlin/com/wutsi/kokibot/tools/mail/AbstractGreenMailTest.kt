package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Flags
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.util.Date

abstract class AbstractGreenMailTest {
    protected lateinit var greenMail: GreenMail
    protected lateinit var guser: GreenMailUser

    protected val email = "test@example.com"
    protected val username = "user"
    protected val password = "password"

    abstract fun port(): Int

    @BeforeEach
    open fun setup() {
        // Starts IMAP, SMTP, and POP3 on random free ports
        greenMail = GreenMail(ServerSetupTest.ALL)
        guser = greenMail.setUser(email, username, password)
        greenMail.start()
    }

    @AfterEach
    open fun tearDown() {
        greenMail.stop()
    }

    protected fun deliver(
        from: String,
        subject: String,
        markAsRead: Boolean = false,
        body: String = "-",
        unsubscribeUrl: String? = null,
        sentDate: Date = Date(),
    ): MimeMessage {
        val message = createMimeMessage(from, subject, markAsRead)
        if (unsubscribeUrl != null) {
            message.addHeader("List-Unsubscribe", "<yo@gmail.com><$unsubscribeUrl>")
        }
        message.setText(body)
        return deliver(message)
    }

    protected fun deliverHtml(from: String, subject: String, body: String = "-", file: File? = null): MimeMessage {
        val multipart = MimeMultipart("alternative")
        val html = MimeBodyPart()
        html.setContent(body, "text/html; charset=utf-8")
        multipart.addBodyPart(html)

        if (file != null) {
            val attachment = MimeBodyPart()
            attachment.attachFile(file)
            multipart.addBodyPart(attachment)
        }

        val message = createMimeMessage(from, subject, false)
        message.setContent(multipart)
        return deliver(message)
    }

    private fun deliver(message: MimeMessage): MimeMessage {
        guser.deliver(message)
        return message
    }

    private fun createMimeMessage(from: String, subject: String, markAsRead: Boolean = false): MimeMessage {
        val session = when (port()) {
            ServerSetupTest.IMAP.port -> greenMail.imap.createSession()
            ServerSetupTest.IMAPS.port -> greenMail.imaps.createSession()
            ServerSetupTest.SMTP.port -> greenMail.smtp.createSession()
            ServerSetupTest.POP3.port -> greenMail.pop3.createSession()
            ServerSetupTest.POP3S.port -> greenMail.pop3s.createSession()
            else -> throw IllegalStateException("Invalid port: ${port()}")
        }

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.addRecipient(Message.RecipientType.TO, InternetAddress(email))
        message.setSubject(subject)

        if (markAsRead) {
            message.setFlag(Flags.Flag.SEEN, true)
        }
        return message
    }
}
