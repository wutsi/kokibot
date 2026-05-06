package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File

class MemoryTest {
    private val home = File("target/test-data/home/memory")
    private val chatHistory = mock<DailyLog>()
    private val llm = mock<LLM>()
    private val window = 5L
    private val maxLength = 2048
    private val context = Context(
        home = home,
        dailyLog = chatHistory,
        llm = llm,
    )

    private val memory = Memory()

    @BeforeEach
    fun setUp() {
        home.deleteRecursively()

        val config = mapOf(
            "window" to "${window}d",
            "max-length" to maxLength,
        )
        memory.init(config, context)
    }

    @Test
    fun id() {
        assertEquals("service:memory", memory.id())
    }

    @Test
    fun compact() {
        // WHEN
        memory.compact()

        // THEN
        val instructions = this::class.java.getResourceAsStream("/instructions/MEMORY.md")!!.bufferedReader().readText()
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{DAYS}}", window.toString())
            .replace("{{MAX_LENGTH}}", maxLength.toString())
        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(emptyList()))
        assertEquals(instructions, req.firstValue.prompt)
    }

    @Test
    fun `get no file`() {
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
        val result = memory.get()

        // THEN
        assertEquals("This is the current memory", result)
    }

    @Test
    fun health() {
        val health = memory.health()
        assertEquals(memory.id(), health.id)
        assertTrue(health.up)
    }
}
