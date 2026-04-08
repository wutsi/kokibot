package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertTrue

class ChatHistoryTest {
    private val jsonMapper = JsonMapper()
    private val home = File("target/test-data/home/chat-history-test")
    private val history = ChatHistory(home, jsonMapper)

    @BeforeEach
    fun setUp() {
        history.clear()
    }

    @Test
    fun `save first and load`() {
        // WHEN
        val prompt = Message("Hello", Role.USER)
        val response = Message("Hi there!", Role.ASSISTANT)
        history.save(prompt, response)

        // THEN
        val loadedHistory = history.load()
        assert(loadedHistory.size == 2)
        assert(loadedHistory[0] == prompt)
        assert(loadedHistory[1] == response)
    }

    @Test
    fun `save multiple and load`() {
        // WHEN
        val prompt1 = Message("Hello", Role.USER)
        val response1 = Message("Hi there!", Role.ASSISTANT)
        history.save(prompt1, response1)

        val prompt2 = Message("How are you", Role.USER)
        val response2 = Message("I'm fine, thank you!", Role.ASSISTANT)
        history.save(prompt2, response2)

        // THEN
        val loadedHistory = history.load()
        assert(loadedHistory.size == 4)
        assert(loadedHistory[0] == prompt1)
        assert(loadedHistory[1] == response1)
        assert(loadedHistory[2] == prompt2)
        assert(loadedHistory[3] == response2)
    }

    @Test
    fun `load empty`() {
        // WHEN
        history.clear()
        val result = history.load()

        // THEN
        assert(result.size == 0)
    }

    @Test
    fun `load json`() {
        // WHEN
        val prompt = Message("Hello", Role.USER)
        val response = Message("Hi there!", Role.ASSISTANT)
        history.save(prompt, response)
        val json = history.loadJson()

        // THEN
        assertNotNull(json)
        assertTrue(json.contains("Hello"))
        assertTrue(json.contains("Hi there!"))
    }

    @Test
    fun `load json empty`() {
        // WHEN
        history.clear()
        val json = history.loadJson()

        // THEN
        assertNull(json)
    }
}
