package com.wutsi.kokibot.service.mail

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class SMTPTest {
    private val email = "test@example.com"
    private val username = "user"
    private val password = "password"
    private val from = "no-reply@gmail.com"

    private lateinit var greenMail: GreenMail
    private lateinit var guser: GreenMailUser
    private val smtp = SMTP()
    private val context = mock<Context>()

    @BeforeEach
    fun setup() {
        // Starts IMAP, SMTP, and POP3 on random free ports
        greenMail = GreenMail(ServerSetupTest.ALL)
        guser = greenMail.setUser(email, username, password)
        greenMail.start()
    }

    @AfterEach
    fun tearDown() {
        greenMail.stop()
    }

    @Test
    fun id() {
        assertEquals("service:smtp", smtp.id())
    }

    @Test
    fun getSession() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTPS.port,
            "username" to username,
            "password" to password,
            "from" to from,
            "use-ssl" to true,
            "use-tls" to false,
        )

        smtp.init(config, context)

        assertEquals(from, smtp.getFrom())
        smtp.getSession()
    }

    @Test
    fun `getProperties SSL`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTPS.port,
            "username" to username,
            "password" to password,
            "from" to from,
            "use-ssl" to true,
        )

        smtp.init(config, context)
        val props = smtp.getProperties()

        assertEquals("localhost", props.getProperty("mail.smtp.host"))
        assertEquals(ServerSetupTest.SMTPS.port.toString(), props.getProperty("mail.smtp.port"))
        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals(null, props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun `getProperties TLS`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTPS.port,
            "username" to username,
            "password" to password,
            "from" to from,
            "use-tls" to true,
        )

        smtp.init(config, context)
        val props = smtp.getProperties()

        assertEquals("localhost", props.getProperty("mail.smtp.host"))
        assertEquals(ServerSetupTest.SMTPS.port.toString(), props.getProperty("mail.smtp.port"))
        assertEquals(null, props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun `getProperties minimal`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTPS.port,
            "username" to username,
            "password" to password,
            "from" to from,
        )

        smtp.init(config, context)
        val props = smtp.getProperties()

        assertEquals("localhost", props.getProperty("mail.smtp.host"))
        assertEquals(ServerSetupTest.SMTPS.port.toString(), props.getProperty("mail.smtp.port"))
        assertEquals(null, props.getProperty("mail.smtp.ssl.enable"))
        assertEquals(null, props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun `init - no SMTP host configuration`() {
        val cfg = mapOf(
            "port" to ServerSetupTest.SMTP.port,
            "username" to username,
            "password" to password,
            "from" to from
        )

        assertThrows<ConfigurationException> { smtp.init(cfg, context) }
    }

    @Test
    fun `init - no SMTP port configuration`() {
        val cfg = mapOf(
            "host" to "localhost",
            "username" to username,
            "password" to password,
            "from" to from
        )

        assertThrows<ConfigurationException> { smtp.init(cfg, context) }
    }

    @Test
    fun `init - no SMTP username configuration`() {
        val cfg = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTP.port,
            "password" to password,
            "from" to from
        )

        assertThrows<ConfigurationException> { smtp.init(cfg, context) }
    }

    @Test
    fun `init - no SMTP password configuration`() {
        val cfg = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTP.port,
            "username" to username,
            "from" to from
        )

        assertThrows<ConfigurationException> { smtp.init(cfg, context) }
    }

    @Test
    fun `init - no SMTP from configuration`() {
        val cfg = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTP.port,
            "username" to username,
            "password" to password
        )

        assertThrows<ConfigurationException> { smtp.init(cfg, context) }
    }

    @Test
    fun `health - up`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTP.port,
            "username" to username,
            "password" to password,
            "from" to from,
        )

        smtp.init(config, context)
        val health = smtp.health()

        assertEquals("service:smtp", health.id)
        assertEquals(true, health.up)
    }

    @Test
    fun `health - down`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to 1111,
            "username" to username,
            "password" to password,
            "from" to from,
        )

        smtp.init(config, context)
        val health = smtp.health()

        assertEquals("service:smtp", health.id)
        assertEquals(false, health.up)
    }
}
