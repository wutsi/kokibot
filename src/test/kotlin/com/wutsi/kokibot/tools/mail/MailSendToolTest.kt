package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolParameterType
import jakarta.mail.Message.RecipientType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    @Test
    fun `exec - missing required argument - to`() {
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "from_name" to "Roger Milla",
                    "subject" to "Hello",
                    "body" to "World...",
                )
            )
        }
    }

    @Test
    fun `exec - missing required argument - subject`() {
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "from_name" to "Roger Milla",
                    "to" to "ray.sponsible@gmail.com",
                    "body" to "World...",
                )
            )
        }
    }

    @Test
    fun `exec - missing required argument - body`() {
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "from_name" to "Roger Milla",
                    "to" to "ray.sponsible@gmail.com",
                    "subject" to "Hello",
                )
            )
        }
    }
}
