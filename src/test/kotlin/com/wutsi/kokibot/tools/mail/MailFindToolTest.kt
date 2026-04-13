package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailFindToolTest : AbstractIMAPToolTest() {
    private val tool = MailFindTool()

    @BeforeEach
    override fun setup() {
        super.setup()
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(MailFindTool.NAME, meta.name)
        assertEquals(2, meta.parameters.size)

        assertEquals("keyword", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("limit", meta.parameters[1].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)
    }

    @Test
    fun `exec - search in subject`() {
        // GIVEN
        val msg1 = deliver("roger.milla@gmail.com", "I've scored 5 goals in the World Cup")
        val msg2 = deliver("ray.sponsible@gmail.com", "Hello world")
        val msg3 = deliver("omam.mbiyic@gmail.com", "What is the color of blue ball")
        val msg4 = deliver("omam.mbiyic@gmail.com", "Im the best football player of the world")
        val msg5 = deliver("omam.mbiyic@gmail.com", "I love it!")

        // WHEN
        val result = tool.exec(
            mapOf("keyword" to "ball")
        )
//        println(result)

        assertEquals(true, result.contains("2 email(s) found with keyword 'ball'"))

        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("Message-ID: ${msg4.messageID}"))
    }

    @Test
    fun `exec - search in body`() {
        // GIVEN
        val msg1 = deliver("roger.milla@gmail.com", subject = "M1", body = "I've scored 5 goals in the World Cup")
        val msg2 = deliver("ray.sponsible@gmail.com", subject = "M2", body = "Hello world")
        val msg3 = deliver("omam.mbiyic@gmail.com", subject = "M3", body = "What is the color of blue ball")
        val msg4 = deliver("omam.mbiyic@gmail.com", subject = "M4", body = "Im the best football player of the world")
        val msg5 = deliver("omam.mbiyic@gmail.com", subject = "M5", body = "I love it!")

        // WHEN
        val result = tool.exec(
            mapOf("keyword" to "ball")
        )
//        println(result)

        assertEquals(true, result.contains("2 email(s) found with keyword 'ball'"))

        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("Message-ID: ${msg4.messageID}"))
    }

    @Test
    fun `exec - search limit`() {
        // GIVEN
        val msg1 = deliver("roger.milla@gmail.com", subject = "M1", body = "I've scored 5 goals in the World Cup")
        val msg2 = deliver("ray.sponsible@gmail.com", subject = "M2", body = "Hello world")
        val msg3 = deliver("omam.mbiyic@gmail.com", subject = "M3", body = "What is the color of blue ball")
        val msg4 = deliver("omam.mbiyic@gmail.com", subject = "M5", body = "I love it!")
        val msg5 = deliver("omam.mbiyic@gmail.com", subject = "M4", body = "Im the best football player of the world")

        // WHEN
        val result = tool.exec(
            mapOf(
                "keyword" to "the",
                "limit" to 2
            )
        )
//        println(result)

        assertEquals(true, result.contains("2 email(s) found with keyword 'the'"))

        assertTrue(result.contains("Message-ID: ${msg3.messageID}"))
        assertTrue(result.contains("Message-ID: ${msg5.messageID}"))
    }

    @Test
    fun `exec - search not found`() {
        // GIVEN
        deliver("roger.milla@gmail.com", subject = "M1", body = "I've scored 5 goals in the World Cup")
        deliver("ray.sponsible@gmail.com", subject = "M2", body = "Hello world")
        deliver("omam.mbiyic@gmail.com", subject = "M3", body = "What is the color of blue ball")
        deliver("omam.mbiyic@gmail.com", subject = "M4", body = "Im the best football player of the world")
        deliver("omam.mbiyic@gmail.com", subject = "M5", body = "I love it!")

        // WHEN
        val result = tool.exec(
            mapOf("keyword" to "xxxx")
        )
//        println(result)

        assertEquals("0 email(s) found with keyword 'xxxx'\n", result)
    }

    @Test
    fun `exec - missing keyword`() {
        // WHEN
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, Any>()) }
    }
}
