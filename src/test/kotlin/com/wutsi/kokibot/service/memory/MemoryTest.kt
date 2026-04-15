package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals

class MemoryTest {
    private val home = File("target/test-data/home/memory")
    private val chatHistory = mock<ChatHistory>()
    private val llm = mock<LLM>()
    private val window = 5L
    private val context = Context(
        home = home,
        chatHistory = chatHistory,
        llm = llm,
    )

    private val memory = Memory()

    @BeforeEach
    fun setUp() {
        home.deleteRecursively()

        val config = mapOf(
            "window" to "${window}d"
        )
        memory.init(config, context)
    }

    @Test
    fun id() {
        assertEquals("service:memory", memory.id())
    }

    @Test
    fun `compact and get`() {
        // GIVEN
        val dir = File(home.absolutePath + "/workspace/memory")
        dir.mkdirs()
        val ff = File(dir, "MEMORY.md")
        ff.writeText("This is the current memory")

        doReturn("M1\nM2\nM3").whenever(chatHistory).merge(any(), any())

        val response = LLMResponse(
            choices = listOf(LLMResponseChoice(content = "Fact1\nFact2\nFact3"))
        )
        doReturn(response).whenever(llm).completion(any(), any())

        // WHEN
        memory.compact()

        // THEN
        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(emptyList()))
        assertTrue(req.firstValue.prompt.contains("This is the current memory"))
        assertTrue(req.firstValue.prompt.contains("M1"))
        assertTrue(req.firstValue.prompt.contains("M2"))
        assertTrue(req.firstValue.prompt.contains("M3"))

        verify(chatHistory).merge(
            LocalDate.now().minusDays(window),
            LocalDate.now(),
        )

        val file = File(home.absolutePath + "/workspace/memory/MEMORY.md")
        assertTrue(file.exists())
        assertEquals("Fact1\nFact2\nFact3", file.readText())

        assertEquals("Fact1\nFact2\nFact3", memory.get())
    }

    @Test
    fun `compact empty response from LLM`() {
        // GIVEN
        doReturn("M1\nM2\nM3").whenever(chatHistory).merge(any(), any())

        val response = LLMResponse(choices = emptyList())
        doReturn(response).whenever(llm).completion(any(), any())

        // WHEN
        memory.compact()

        // THEN
        val file = File(home.absolutePath + "/workspace/memory/MEMORY.md")
        assertTrue(file.exists())
        assertEquals("", file.readText())

        assertEquals("", memory.get())
    }

    @Test
    fun `compact no chat history`() {
        // GIVEN
        doReturn(null).whenever(chatHistory).merge(any(), any())

        // WHEN
        memory.compact()

        // THEN
        verify(llm, never()).completion(any(), any())

        val file = File(home.absolutePath + "/workspace/memory/MEMORY.md")

        assertFalse(file.exists())
        assertNull(memory.get())

        verify(chatHistory, never()).clear()
    }

    @Test
    fun `get no file`() {
        val result = memory.get()
        assertNull(result)
    }

    @Test
    fun `get file`() {
        // GIVEN
        val dir = File(home.absolutePath + "/workspace/memory")
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
