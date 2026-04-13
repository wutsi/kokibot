package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailListToolTest : AbstractIMAPToolTest() {
    private val tool = MailListTool()

    @BeforeEach
    override fun setup() {
        super.setup()
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(MailListTool.NAME, meta.name)
        assertEquals(3, meta.parameters.size)

        assertEquals("unread", meta.parameters[0].name)
        assertEquals(ToolParameterType.BOOLEAN, meta.parameters[0].type)
        assertFalse(meta.parameters[0].required)

        assertEquals("earliest", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)

        assertEquals("limit", meta.parameters[2].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[2].type)
        assertFalse(meta.parameters[1].required)
    }

    @Test
    fun `exec - no email received`() {
        val result = tool.exec(emptyMap<String, Any>())

        assertEquals(true, result.startsWith("0 email(s) found"))
    }

    @Test
    fun `exec - all emails`() {
        // GIVEN
        val msg1 = deliver(
            "roger.milla@gmail.com",
            "I've scored 5 goals in the World Cup",
            unsubscribeUrl = "https://www.unsubscribe.com/201930293"
        )
        val msg2 = deliver("ray.sponsible@gmail.com", "Hello")
        val msg3 = deliver("omam.mbiyic@gmail.com", "Yo man")

        // WHEN
        val result = tool.exec(emptyMap<String, Any>())
//        println(result)

        // THEN
        assertEquals(true, result.contains("3 email(s) found"))

        assertTrue(result.contains("Email #1:"))
        assertTrue(result.contains("Message-ID: ${msg1.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: https://www.unsubscribe.com/201930293"))
        assertTrue(result.contains("From: roger.milla@gmail.com"))
        assertTrue(result.contains("Subject: I've scored 5 goals in the World Cup"))

        assertTrue(result.contains("Email #2:"))
        assertTrue(result.contains("Message-ID: ${msg2.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: N/A"))
        assertTrue(result.contains("From: ray.sponsible@gmail.com"))
        assertTrue(result.contains("Subject: Hello"))

        assertTrue(result.contains("Email #3:"))
        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("From: omam.mbiyic@gmail.com"))
        assertTrue(result.contains("Subject: Yo man"))
    }

    @Test
    fun `exec - limit emails`() {
        // GIVEN
        val msg1 = deliver(
            "roger.milla@gmail.com",
            "I've scored 5 goals in the World Cup",
            unsubscribeUrl = "https://www.unsubscribe.com/201930293"
        )
        val msg2 = deliver("ray.sponsible@gmail.com", "Hello")
        val msg3 = deliver("omam.mbiyic@gmail.com", "Yo man")

        // WHEN
        val result = tool.exec(mapOf("limit" to 2))
//        println(result)

        // THEN
        assertEquals(true, result.contains("2 email(s) found"))

        assertFalse(result.contains("Email #1:"))
        assertFalse(result.contains("Message-ID: ${msg1.messageID}"))
        assertFalse(result.contains("Unsubscribe-URL: https://www.unsubscribe.com/201930293"))
        assertFalse(result.contains("From: roger.milla@gmail.com"))
        assertFalse(result.contains("Subject: I've scored 5 goals in the World Cup"))

        assertTrue(result.contains("Email #2:"))
        assertTrue(result.contains("Message-ID: ${msg2.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: N/A"))
        assertTrue(result.contains("From: ray.sponsible@gmail.com"))
        assertTrue(result.contains("Subject: Hello"))

        assertTrue(result.contains("Email #3:"))
        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("From: omam.mbiyic@gmail.com"))
        assertTrue(result.contains("Subject: Yo man"))
    }

    @Test
    fun `exec - unread emails`() {
        // GIVEN
        val msg1 = deliver("roger.milla@gmail.com", "I've scored 5 goals in the World Cup", false)
        val msg2 = deliver("ray.sponsible@gmail.com", "Hello", true)
        val msg3 = deliver("omam.mbiyic@gmail.com", "Yo man", false)

        // WHEN
        val result = tool.exec(
            mapOf(
                "unread" to true,
            )
        )
//        println(result)

        // THEN
        assertEquals(true, result.contains("2 unread email(s) found"))

        assertTrue(result.contains("Email #3:"))
        assertTrue(result.contains("Message-ID: ${msg1.messageID}"))
        assertTrue(result.contains("From: roger.milla@gmail.com"))
        assertTrue(result.contains("Subject: I've scored 5 goals in the World Cup"))

        assertFalse(result.contains("Email #2:"))
        assertFalse(result.contains("Message-ID: ${msg2.messageID}"))
        assertFalse(result.contains("From: ray.sponsible@gmail.com"))
        assertFalse(result.contains("Subject: Hello"))

        assertTrue(result.contains("Email #1:"))
        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("From: omam.mbiyic@gmail.com"))
        assertTrue(result.contains("Subject: Yo man"))
    }

    @Test
    fun `exec - by date`() {
        // GIVEN
        val msg1 = deliver(
            "roger.milla@gmail.com",
            "I've scored 5 goals in the World Cup",
            unsubscribeUrl = "https://www.unsubscribe.com/201930293",
        )
        val msg2 = deliver("ray.sponsible@gmail.com", "Hello")
        val msg3 = deliver("omam.mbiyic@gmail.com", "Yo man")

        // WHEN
        val result = tool.exec(mapOf("earliest" to "1d"))
//        println(result)

        // THEN
        assertEquals(true, result.contains("3 email(s) found"))

        assertTrue(result.contains("Email #1:"))
        assertTrue(result.contains("Message-ID: ${msg1.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: https://www.unsubscribe.com/201930293"))
        assertTrue(result.contains("From: roger.milla@gmail.com"))
        assertTrue(result.contains("Subject: I've scored 5 goals in the World Cup"))

        assertTrue(result.contains("Email #2:"))
        assertTrue(result.contains("Message-ID: ${msg2.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: N/A"))
        assertTrue(result.contains("From: ray.sponsible@gmail.com"))
        assertTrue(result.contains("Subject: Hello"))

        assertTrue(result.contains("Email #3:"))
        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("From: omam.mbiyic@gmail.com"))
        assertTrue(result.contains("Subject: Yo man"))
    }

    @Test
    fun `earliest 1d`() {
        val value = tool.earliestValue("1d")
        assertEquals(86400000L, value)
    }

    @Test
    fun `earliest 2d`() {
        val value = tool.earliestValue("3d")
        assertEquals(259200000L, value)
    }

    @Test
    fun `earliest 3h`() {
        val value = tool.earliestValue("3h")
        assertEquals(10800000L, value)
    }

    @Test
    fun `earliest 30m`() {
        val value = tool.earliestValue("30m")
        assertEquals(1800000L, value)
    }

    @Test
    fun `earliest invalid value`() {
        val value = tool.earliestValue("xxx")
        assertEquals(86400000L, value)
    }

    @Test
    fun `earliest missing value`() {
        val value = tool.earliestValue("")
        assertEquals(86400000L, value)
    }
}
