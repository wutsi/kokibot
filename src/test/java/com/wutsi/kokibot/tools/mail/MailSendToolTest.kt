package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.ToolRegistry
import jakarta.mail.Message.RecipientType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.UUID

class MailSendToolTest : AbstractSMTPToolTest() {
    private val tool = MailSendTool()

    @BeforeEach
    override fun setup() {
        super.setup()
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(MailSendTool.NAME, meta.name)
        assertEquals(5, meta.parameters.size)

        assertEquals("from_name", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertEquals(false, meta.parameters[0].required)

        assertEquals("to", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertEquals(true, meta.parameters[1].required)

        assertEquals("subject", meta.parameters[2].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[2].type)
        assertEquals(true, meta.parameters[2].required)

        assertEquals("body", meta.parameters[3].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[3].type)
        assertEquals(true, meta.parameters[3].required)

        assertEquals("reply_message_id", meta.parameters[4].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[4].type)
        assertEquals(false, meta.parameters[4].required)
    }

    @Test
    fun `exec - send message`() {
        // WHEN
        val result = tool.exec(
            mapOf(
                "from_name" to "Roger Milla",
                "to" to "ray.sponsible@gmail.com",
                "subject" to "Hello",
                "body" to "World...",
            )
        )

        // THEN
        assertEquals("SUCCESS - Email sent to ray.sponsible@gmail.com", result)

        val messages = greenMail.receivedMessages
        assertTrue(messages.isNotEmpty())
        assertEquals("Hello", messages[0].subject)
        assertEquals("World...", messages[0].content.toString())
        assertEquals(1, messages[0].getRecipients(RecipientType.TO).size)
        assertEquals("ray.sponsible@gmail.com", messages[0].getRecipients(RecipientType.TO)[0].toString())
        assertEquals("Roger Milla <$from>", messages[0].from[0].toString())
        assertEquals(null, messages[0].getHeader("In-Reply-To")?.get(0))
        assertEquals(null, messages[0].getHeader("References")?.get(0))
    }

    @Test
    fun `exec - reply to message`() {
        // GIVEN
        val messageID = UUID.randomUUID().toString()

        // WHEN
        val result = tool.exec(
            mapOf(
                "reply_message_id" to messageID,
                "to" to "ray.sponsible@gmail.com",
                "subject" to "Hello",
                "body" to "World...",
            )
        )

        // THEN
        assertEquals("SUCCESS - Email sent to ray.sponsible@gmail.com", result)

        val messages = greenMail.receivedMessages
        assertTrue(messages.isNotEmpty())
        assertEquals("Hello", messages[0].subject)
        assertEquals("World...", messages[0].content.toString())
        assertEquals(1, messages[0].getRecipients(RecipientType.TO).size)
        assertEquals("ray.sponsible@gmail.com", messages[0].getRecipients(RecipientType.TO)[0].toString())
        assertEquals(from, messages[0].from[0].toString())
        assertEquals(messageID, messages[0].getHeader("In-Reply-To")?.get(0))
        assertEquals(messageID, messages[0].getHeader("References")?.get(0))
    }

    private fun createContext(config: Map<*, *>): Context {
        return Context(
            home = File("target/test-data/" + this::class.java.simpleName),
            llm = mock<LLM>(),
            toolRegistry = mock<ToolRegistry>(),
            chatHistory = mock<ChatHistory>(),
            config = emptyMap<String, Any>()
        )
    }

    @Test
    fun `init - no mail configuration`() {
        val ctx = createContext(emptyMap<String, Any>())

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to emptyMap<String, Any>()
            )
        )

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP host configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to mapOf(
                    "smtp" to mapOf(
                        "port" to ServerSetupTest.SMTP.port,
                        "username" to username,
                        "password" to password,
                        "from" to from
                    )
                )
            )
        )

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP port configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to mapOf(
                    "smtp" to mapOf(
                        "host" to "localhost",
                        "username" to username,
                        "password" to password,
                        "from" to from
                    )
                )
            )
        )
        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP username configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to mapOf(
                    "smtp" to mapOf(
                        "host" to "localhost",
                        "port" to ServerSetupTest.SMTP.port,
                        "password" to password,
                        "from" to from
                    )
                )
            )
        )

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP password configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to mapOf(
                    "smtp" to mapOf(
                        "host" to "localhost",
                        "port" to ServerSetupTest.SMTP.port,
                        "username" to username,
                        "from" to from
                    )
                )
            )
        )

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }

    @Test
    fun `init - no SMTP from configuration`() {
        val ctx = createContext(
            mapOf(
                "mail" to mapOf(
                    "smtp" to mapOf(
                        "host" to "localhost",
                        "port" to ServerSetupTest.SMTP.port,
                        "username" to username,
                        "password" to password
                    )
                )
            )
        )

        assertThrows<ConfigurationException> { tool.init(emptyMap<String, Any>(), ctx) }
    }
}
