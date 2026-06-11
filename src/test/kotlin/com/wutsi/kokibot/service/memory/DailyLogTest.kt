package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File
import java.time.LocalDate
import kotlin.test.assertTrue

class DailyLogTest {
    private val home = File("target/test-data/home/daily-log-test")
    val context = Context(
        home = home,
        llm = mock<LLM>()
    )
    private val log = DailyLog()

    @BeforeEach
    fun setUp() {
        home.deleteRecursively()

        log.init(emptyMap<String, Any>(), context)
        log.clear()
    }

    @Test
    fun id() {
        assertEquals("service:daily-log", log.id())
    }

    @Test
    fun get() {
        // GIVEN
        val file = File(home.absolutePath + "/memory/history/${LocalDate.now()}.md")
        file.parentFile.mkdirs()
        file.writeText("Hello\nHi there!")

        // WHEN
        val result = log.get()

        // THEN
        assertNotNull(result)
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("Hi there!"))
    }

    @Test
    fun `get empty`() {
        // WHEN
        val result = log.get()

        // THEN
        assertNull(result)
    }

    @Test
    fun clear() {
        // GIVEN
        val file = File(home.absolutePath + "/memory/history/${LocalDate.now()}.md")
        file.parentFile.mkdirs()
        file.writeText("Hello\nHi there!")

        // WHEN
        log.clear()

        // THEN
        assertNull(log.get())
    }

    @Test
    fun health() {
        val health = log.health()

        assertEquals(log.id(), health.id)
        assertTrue(health.up)
    }
}
