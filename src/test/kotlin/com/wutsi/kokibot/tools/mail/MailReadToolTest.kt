package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class MailReadToolTest : AbstractIMAPToolTest() {
    private val tool = MailReadTool()

    @BeforeEach
    override fun setup() {
        super.setup()
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        kotlin.test.assertEquals(MailReadTool.NAME, meta.name)
        kotlin.test.assertEquals(1, meta.parameters.size)

        kotlin.test.assertEquals("message_id", meta.parameters[0].name)
        kotlin.test.assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `read - text`() {
        val msg = deliver(
            "ray.sponsible@gmail.com",
            "Yo man!!",
            false,
            "Hello world",
            unsubscribeUrl = "https://www.unsubscribe.com"
        )

        val result = tool.exec(mapOf("message_id" to msg.messageID))
//        println(result)

        assertTrue(result.contains("Here are the details of the email ${msg.messageID}:"))
        assertTrue(result.contains("Message-ID: ${msg.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: https://www.unsubscribe.com"))
        assertTrue(result.contains("From: ray.sponsible@gmail.com"))
        assertTrue(result.contains("Subject: Yo man!!"))
        assertTrue(result.contains("Hello world"))
    }

    @Test
    fun `read - html`() {
        deliver("roger.milla@gmail.com", "I've got a question for you")
        val msg = deliverHtml("ray.sponsible@gmail.com", "Yo man!!", "Hello <b>world</b>")

        val result = tool.exec(mapOf("message_id" to msg.messageID))
//        println(result)

        assertTrue(result.contains("Here are the details of the email ${msg.messageID}:"))
        assertTrue(result.contains("Message-ID: ${msg.messageID}"))
        assertTrue(result.contains("Unsubscribe-URL: N/A"))
        assertTrue(result.contains("From: ray.sponsible@gmail.com"))
        assertTrue(result.contains("Subject: Yo man!!"))
        assertTrue(result.contains("Hello **world**"))
    }

    @Test
    fun `read - not found`() {
        val result = tool.exec(mapOf("message_id" to "<100>"))
//        println(result)

        assertEquals("Email <100> not found", result)
    }

    @Test
    fun `exec - missing messageId`() {
        // WHEN
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, Any>()) }
    }
}
