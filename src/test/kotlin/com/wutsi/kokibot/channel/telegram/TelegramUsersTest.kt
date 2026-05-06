package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramUsersTest {
    private val context = Context(
        home = File("target/telegram-users"),
        llm = mock()
    )
    private val users = TelegramUsers()

    @BeforeEach
    fun setUp() {
        context.home.deleteRecursively()
        users.init(context)
    }

    @Test
    fun put() {
        users.put("john", "123456789")

        assertEquals("123456789", users.get("john"))
        val file = File(context.home, "telegram/users.properties")
        assertEquals(true, file.exists())
    }

    @Test
    fun `put twice - persist only once`() {
        users.put("john", "123456789")
        Thread.sleep(1000)
        users.put("john", "123456789")

        assertEquals("123456789", users.get("john"))
        val file = File(context.home, "telegram/users.properties")
        assertTrue(System.currentTimeMillis() - file.lastModified() >= 1000)
    }

    @Test
    fun `get - bad user`() {
        assertEquals(null, users.get("xxx"))
    }

    @Test
    fun `init - load data`() {
        val file = File(context.home, "telegram/users.properties")
        file.writeText("john=123456789")

        users.init(context)

        assertEquals("123456789", users.get("john"))
    }
}
