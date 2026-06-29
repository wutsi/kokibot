package com.wutsi.kokibot.channel.email

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.service.inbox.Inbox
import jakarta.mail.Flags
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File

class EmailChannelTest {
    private val channel = EmailChannel()
    private lateinit var greenMail: GreenMail
    private lateinit var guser: GreenMailUser

    private val email = "test@example.com"
    private val username = "user"
    private val password = "password"
    val config = mapOf(
        "email" to email,
        "username" to username,
        "password" to password,

        "imap-protocol" to "imap",
        "imap-host" to "localhost",
        "imap-port" to ServerSetupTest.IMAP.port,
        "imap-ssl" to false,

        "smtp-host" to "localhost",
        "smtp-port" to ServerSetupTest.SMTP.port,
        "smtp-ssl" to false,
    )
    private val inbox = mock(Inbox::class.java)
    private val context = Context(
        home = File("target/test-data/email-channel"),
        llm = mock(),
        assistant = mock(),
        inbox = inbox,
    )

    @BeforeEach
    fun setup() {
        // Starts IMAP, SMTP, and POP3 on random free ports
        greenMail = GreenMail(ServerSetupTest.ALL)
        guser = greenMail.setUser(email, username, password)
        greenMail.start()

        context.fileService.init(config, context)
        channel.init(config, context)
    }

    @AfterEach
    fun tearDown() {
        greenMail.stop()
        channel.destroy()
    }

    @Test
    fun id() {
        assertEquals("channel:email", channel.id())
    }

    @Test
    fun source() {
        assertEquals(email, channel.source())
    }

    @Test
    fun health() {
        // WHEN
        val result = channel.health()

        // THEN
        assertEquals(true, result.up)
        assertEquals("channel:email", result.id)
    }

    @Test
    fun `health - bad SMTP configuration`() {
        // GIVEN
        val cfg = config + mapOf("smtp-host" to "invalid-host")
        channel.init(cfg, context)

        // WHEN
        val result = channel.health()

        // THEN
        assertEquals(false, result.up)
        assertEquals("channel:email", result.id)
    }

    @Test
    fun `health - bad IMAP configuration`() {
        // GIVEN
        val cfg = config + mapOf("imap-host" to "invalid-host")
        channel.init(cfg, context)

        // WHEN
        val result = channel.health()

        // THEN
        assertEquals(false, result.up)
        assertEquals("channel:email", result.id)
    }

    @Test
    fun `send - success`() {
        // WHEN
        val result = channel.send(
            com.wutsi.kokibot.Message(
                text = "This is a test message",
                subject = "Test",
                role = Role.ASSISTANT,
                userId = "ray.sponsible@gmail.com",
                filePaths = listOf(
                    this::class.java.getResource("/file/document-en.pdf")!!.file,
                    this::class.java.getResource("/file/sample.docx")!!.file,
                )
            )
        )

        // THEN
        assertEquals(true, result)

        val replies = greenMail.receivedMessages
        assertEquals(1, replies.size)

        val reply = replies[0]
        val bodyParts = (reply.content as Multipart)
        assertEquals(email, reply.from.firstOrNull()?.toString())
        assertEquals(
            "ray.sponsible@gmail.com",
            (reply.getRecipients(Message.RecipientType.TO).firstOrNull() as InternetAddress).address
        )
        assertEquals("Test", reply.subject)
        assertEquals(null, reply.getHeader("In-Reply-To")?.get(0))
        assertEquals(null, reply.getHeader("References")?.get(0))
        assertEquals(true, reply.contentType.startsWith("multipart/mixed;"))
        assertEquals(3, bodyParts.count)
        assertEquals(true, bodyParts.getBodyPart(0).content.toString().contains("This is a test message"))
    }

    @Test
    fun `send - failure`() {
        // GIVEN
        val cfg = config + mapOf("smtp-host" to "invalid-host")
        channel.init(cfg, context)

        // WHEN
        val result = channel.send(
            com.wutsi.kokibot.Message(
                text = "This is a test message",
                subject = "Test",
                role = Role.ASSISTANT,
                userId = "ray.sponsible@gmail.com"
            )
        )

        // THEN
        assertEquals(false, result)

        val replies = greenMail.receivedMessages
        assertEquals(0, replies.size)
    }

    @Test
    fun `fetch - no message available`() {
        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - unread TXT message`() {
        // GIVEN
        val message = createTextMessage(
            from = "ray.sponsible@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        val prompt = argumentCaptor<com.wutsi.kokibot.Message>()
        verify(inbox).submit(prompt.capture())

        assertEquals(message.messageID, prompt.firstValue.id)
        assertEquals("ray.sponsible@gmail.com", prompt.firstValue.userId)
        assertEquals("channel:email", prompt.firstValue.channelId)
        assertEquals("${message.subject}\nHello\n\nCan you send me my daily debriefing?", prompt.firstValue.text)
        assertEquals(message.subject, prompt.firstValue.subject)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(0, prompt.firstValue.filePaths.size)
    }

    @Test
    fun `fetch - unread HTML message`() {
        // GIVEN
        val message = createMimeMessage(
            from = "ray.sponsible@gmail.com",
            subject = "Hello World",
            body = "<h1>Hello</h1>Can you send me my daily debriefing?",
            markAsRead = false,
            contentType = "text/html",
            attachments = listOf(
                File(this::class.java.getResource("/file/document-en.pdf")!!.file),
                File(this::class.java.getResource("/file/sample.docx")!!.file),
            )
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        val prompt = argumentCaptor<com.wutsi.kokibot.Message>()
        verify(inbox).submit(prompt.capture())

        assertEquals(message.messageID, prompt.firstValue.id)
        assertEquals("ray.sponsible@gmail.com", prompt.firstValue.userId)
        assertEquals("channel:email", prompt.firstValue.channelId)
        assertEquals(
            """
                ${message.subject}
                Hello
                =====

                Can you send me my daily debriefing?


            """.trimIndent(),
            prompt.firstValue.text,
        )
        assertEquals(message.subject, prompt.firstValue.subject)
        assertEquals(Role.USER, prompt.firstValue.role)
        assertEquals(2, prompt.firstValue.filePaths.size)
    }

    @Test
    fun `fetch - accepted from whitelist`() {
        // GIVEN
        val cfg = config + mapOf("sender-whitelist" to listOf("ray.sponsible@gmail.com"))
        channel.init(cfg, context)

        val message = createTextMessage(
            from = "ray.sponsible@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox).submit(any())
    }

    @Test
    fun `fetch - rejected from whitelist`() {
        // GIVEN
        val cfg = config + mapOf("sender-whitelist" to listOf("john.smith@gmail.com"))
        channel.init(cfg, context)

        val message = createTextMessage(
            from = "ray.sponsible@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - rejected from noreply`() {
        // GIVEN
        val message = createTextMessage(
            from = "noreply@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - rejected from no-reply`() {
        // GIVEN
        val message = createTextMessage(
            from = "no-reply@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - rejected from bounce`() {
        // GIVEN
        val message = createTextMessage(
            from = "bounce@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - rejected from mailer-daemon`() {
        // GIVEN
        val message = createTextMessage(
            from = "mailer-daemon@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun `fetch - email error`() {
        // GIVEN
        val cfg = config + mapOf("imap-host" to "invalid-host")
        channel.init(cfg, context)

        // WHEN
        channel.fetch()

        // THEN
        verify(inbox, never()).submit(any())
    }

    @Test
    fun runJob() {
        // GIVEN
        val message = createTextMessage(
            from = "ray.sponsible@gmail.com",
            subject = "Hello World",
            body = "Hello\n\nCan you send me my daily debriefing?",
            markAsRead = false
        )
        deliver(message)

        val cfg = config + mapOf("fetch-frequency" to "5s")
        channel.init(cfg, context)

        // WHEN
        println("Waiting for the scheduled job to run...")
        Thread.sleep(10000) // Wait for the scheduled job to run at least once
        channel.fetch()

        // THEN
        verify(inbox).submit(any())
    }

    private fun createTextMessage(
        from: String,
        subject: String,
        body: String,
        markAsRead: Boolean = false,
    ): MimeMessage {
        val session = greenMail.imap.createSession()
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.addRecipient(Message.RecipientType.TO, InternetAddress(email))
        message.setSubject(subject)
        if (markAsRead) {
            message.setFlag(Flags.Flag.SEEN, true)
        }
        message.setText(body)

        return message
    }

    private fun createMimeMessage(
        from: String,
        subject: String,
        body: String,
        contentType: String = "text/plain",
        markAsRead: Boolean = false,
        attachments: List<File> = emptyList(),
    ): MimeMessage {
        val session = greenMail.imap.createSession()
        val message = MimeMessage(session)

        message.setFrom(InternetAddress(from))
        message.addRecipient(Message.RecipientType.TO, InternetAddress(email))
        message.setSubject(subject)
        if (markAsRead) {
            message.setFlag(Flags.Flag.SEEN, true)
        }

        val multipart = MimeMultipart("alternative")
        val bodyPart = MimeBodyPart()
        bodyPart.setContent(body, "$contentType; charset=utf-8")
        multipart.addBodyPart(bodyPart)

        attachments.forEach { file ->
            val attachmentPart = MimeBodyPart()
            attachmentPart.attachFile(file)
            multipart.addBodyPart(attachmentPart)
        }

        message.setContent(multipart)
        return message
    }

    private fun deliver(message: MimeMessage): MimeMessage {
        guser.deliver(message)
        return message
    }
}
