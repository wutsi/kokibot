package com.wutsi.kokibot.service.mail

import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class IMAPTest {
    private val email = "test@example.com"
    private val username = "user"
    private val password = "password"

    private lateinit var greenMail: GreenMail
    private lateinit var guser: GreenMailUser
    private val imap = IMAP()
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
        assertEquals("service:imap", imap.id())
    }

    @Test
    fun getStore() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAP.port,
            "username" to username,
            "password" to password,
            "use-ssl" to false,
        )

        imap.init(config, context)
        val store = imap.getStore()

        store.close()
    }

    @Test
    fun `getProperties - imap`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAP.port,
            "username" to username,
            "password" to password,
            "use-ssl" to false,
        )

        imap.init(config, context)
        val props = imap.getProperties()

        assertEquals("imap", props["mail.store.protocol"])
        assertEquals("localhost", props["mail.imap.host"])
        assertEquals(ServerSetupTest.IMAP.port.toString(), props["mail.imap.port"])
    }

    @Test
    fun `getProperties - imaps`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAPS.port,
            "username" to username,
            "password" to password,
            "use-ssl" to true,
        )

        imap.init(config, context)
        val props = imap.getProperties()

        assertEquals("imaps", props["mail.store.protocol"])
        assertEquals("localhost", props["mail.imaps.host"])
        assertEquals(ServerSetupTest.IMAPS.port.toString(), props["mail.imaps.port"])
        assertEquals("true", props["mail.imaps.ssl.enable"])
    }

    @Test
    fun `init - no host`() {
        val cfg = mapOf(
            "port" to ServerSetupTest.IMAPS.port,
            "username" to username,
            "password" to password
        )

        assertThrows<ConfigurationException> { imap.init(cfg, context) }
    }

    @Test
    fun `init - no port`() {
        val cfg = mapOf(
            "host" to "localhost",
            "username" to username,
            "password" to password
        )

        assertThrows<ConfigurationException> { imap.init(cfg, context) }
    }

    @Test
    fun `init - no username`() {
        val cfg = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAPS.port,
            "password" to password
        )

        assertThrows<ConfigurationException> { imap.init(cfg, context) }
    }

    @Test
    fun `init - no password`() {
        val cfg = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAPS.port,
            "username" to username,
        )

        assertThrows<ConfigurationException> { imap.init(cfg, context) }
    }

    @Test
    fun `health - up`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAP.port,
            "username" to username,
            "password" to password,
            "use-ssl" to false,
        )

        imap.init(config, context)
        val health = imap.health()

        assertEquals(imap.id(), health.id)
        assertEquals(true, health.up)
    }

    @Test
    fun `health - down`() {
        val config = mapOf(
            "host" to "localhost",
            "port" to 11111,
            "username" to username,
            "password" to password,
            "use-ssl" to false,
        )

        imap.init(config, context)
        val health = imap.health()

        assertEquals(imap.id(), health.id)
        assertEquals(false, health.up)
    }
}
