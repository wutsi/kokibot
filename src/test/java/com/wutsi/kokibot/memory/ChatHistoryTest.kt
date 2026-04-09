package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.LocalDate
import kotlin.test.assertTrue

class ChatHistoryTest {
    private val jsonMapper = JsonMapper()
    private val home = File("target/test-data/home/chat-history-test")
    val context = Context(
        home = home,
        jsonMapper = jsonMapper,
        llm = mock<LLM>()
    )
    private val history = ChatHistory()

    @BeforeEach
    fun setUp() {
        home.deleteRecursively()

        history.init(emptyMap<String, Any>(), context)
        history.clear()
    }

    @Test
    fun `append first and get`() {
        // WHEN
        val prompt = Message("Hello", Role.USER)
        val response = Message("Hi there!", Role.ASSISTANT)
        history.append(prompt, response)

        // THEN
        val history = history.get()
        val messages = jsonMapper.readValue(history, Array<Message>::class.java)
        assertEquals(prompt, messages[0])
        assertEquals(response, messages[1])
    }

    @Test
    fun `append multiple and load`() {
        // WHEN
        val prompt1 = Message("Hello", Role.USER)
        val response1 = Message("Hi there!", Role.ASSISTANT)
        history.append(prompt1, response1)

        val prompt2 = Message("How are you", Role.USER)
        val response2 = Message("I'm fine, thank you!", Role.ASSISTANT)
        history.append(prompt2, response2)

        // THEN
        val history = history.get()
        val messages = jsonMapper.readValue(history, Array<Message>::class.java)
        assertEquals(prompt1, messages[0])
        assertEquals(response1, messages[1])
        assertEquals(prompt2, messages[2])
        assertEquals(response2, messages[3])
    }

    @Test
    fun get() {
        // WHEN
        val prompt = Message("Hello", Role.USER)
        val response = Message("Hi there!", Role.ASSISTANT)
        history.append(prompt, response)
        val json = history.get()

        // THEN
        assertNotNull(json)
        assertTrue(json.contains("Hello"))
        assertTrue(json.contains("Hi there!"))
    }

    @Test
    fun `get empty`() {
        // WHEN
        history.clear()
        val json = history.get()

        // THEN
        assertNull(json)
    }

    @Test
    fun merge() {
        // GIVEN
        val date0 = LocalDate.now()
        val prompt0 = Message("Hello0", Role.USER)
        val response0 = Message("Hi there0!", Role.ASSISTANT)
        history.append(prompt0, response0, date0)

        val date1 = date0.minusDays(1)
        val prompt1 = Message("Hello1", Role.USER)
        val response1 = Message("Hi there1!", Role.ASSISTANT)
        history.append(prompt1, response1, date1)

        val date2 = date1.minusDays(1)
        val prompt2 = Message("Hello2", Role.USER)
        val response2 = Message("Hi there2!", Role.ASSISTANT)
        history.append(prompt2, response2, date2)

        // WHEN
        val history = history.merge(date1, date0)
        val messages = jsonMapper.readValue(history, Array<Message>::class.java)

        // THEN
        assertEquals(4, messages.size)
        assertEquals(prompt1, messages[0])
        assertEquals(response1, messages[1])
        assertEquals(prompt0, messages[2])
        assertEquals(response0, messages[3])
    }

    @Test
    fun `merge - empty`() {
        // GIVEN
        val date0 = LocalDate.now()
        val date1 = date0.minusDays(1)

        // WHEN
        val history = history.merge(date1, date0)

        // THEN
        assertNull(history)
    }
}
