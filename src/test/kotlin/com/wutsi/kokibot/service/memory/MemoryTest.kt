package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File

class MemoryTest {
    private val home = File("target/test-data/home/memory")
    private val window = 5L
    private val maxLength = 2048
    private val context = Context(
        home = home,
        dailyLog = mock(),
        llm = mock(),
        assistant = mock(),
    )
    private val config = mapOf(
        "window" to "${window}d",
        "max-length" to maxLength,
    )

    private val memory = Memory()

    @BeforeEach
    fun setUp() {
        home.deleteRecursively()
    }

    @AfterEach
    fun tearDown() {
        try {
            memory.destroy()
        } catch (_: Exception) {

        }
    }

    @Test
    fun id() {
        assertEquals("service:memory", memory.id())
    }

    @Test
    fun compact() {
        // WHEN
        memory.init(config, context)
        memory.compact()

        // THEN
        val instructions = this::class.java.getResourceAsStream("/instructions/MEMORY.md")!!.bufferedReader().readText()
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{DAYS}}", window.toString())
            .replace("{{MAX_LENGTH}}", maxLength.toString())

        val req = argumentCaptor<Message>()
        verify(context.assistant).process(req.capture(), anyOrNull())
        assertEquals(instructions, req.firstValue.text)
    }

    @Test
    fun `launch compaction`() {
        val cfg = mapOf(
            "compaction-frequency" to "2s",
        )
        memory.init(cfg, context)

        Thread.sleep(3000)

        verify(context.assistant).process(any(), anyOrNull())
    }


    @Test
    fun `launch compaction with errors`() {
        doThrow(RuntimeException::class).whenever(context.assistant).process(any(), anyOrNull())

        val cfg = mapOf(
            "compaction-frequency" to "2s",
        )
        memory.init(cfg, context)

        Thread.sleep(3000)

        verify(context.assistant).process(any(), anyOrNull())
    }

    @Test
    fun `get no file`() {
        memory.init(config, context)
        val result = memory.get()

        assertNull(result)
    }

    @Test
    fun `get with file`() {
        // GIVEN
        val dir = File(home.absolutePath + "/memory")
        dir.mkdirs()
        val ff = File(dir, "MEMORY.md")
        ff.writeText("This is the current memory")

        // WHEN
        memory.init(config, context)
        val result = memory.get()

        // THEN
        assertEquals("This is the current memory", result)
    }

    @Test
    fun health() {
        memory.init(config, context)
        val health = memory.health()
        assertEquals(memory.id(), health.id)
        assertTrue(health.up)
    }
}
