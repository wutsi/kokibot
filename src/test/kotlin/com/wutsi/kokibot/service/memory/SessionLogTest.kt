package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.llm.LLMToolCall
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class SessionLogTest {
    private val log = SessionLog()
    private val context = Context(
        home = File("target/test-data/session-log"),
        llm = mock()
    )

    @BeforeEach
    fun setUp() {
        context.home.deleteRecursively()

        log.init(emptyMap<Any, Any>(), context)
    }

    @Test
    fun id() {
        assertEquals("session-log", log.id())
    }

    @Test
    fun onQuery() {
        val query = Message(
            role = Role.USER,
            userId = "u1",
            channelId = "c1",
            text = "Hello",
            filePaths = listOf("file1.txt")
        )
        log.onQuery("1", 0, query)

        val sessions = log.get("1")
        assertEquals(1, sessions.size)
        assertEquals(Role.USER, sessions[0].role)
        assertEquals("u1", sessions[0].userId)
        assertEquals("c1", sessions[0].channelId)
        assertEquals(2, sessions[0].content.size)
        assertEquals("text", sessions[0].content[0].type)
        assertEquals("Hello", sessions[0].content[0].text)
        assertEquals("file", sessions[0].content[1].type)
        assertEquals("file1.txt", sessions[0].content[1].text)
    }

    @Test
    fun onResponse() {
        val response = Message(
            role = Role.ASSISTANT,
            userId = "u1",
            channelId = "c1",
            text = "Hi there!"
        )
        log.onResponse("2", response)

        val sessions = log.get("2")
        assertEquals(1, sessions.size)
        assertEquals(Role.ASSISTANT, sessions[0].role)
        assertEquals("u1", sessions[0].userId)
        assertEquals("c1", sessions[0].channelId)
        assertEquals(2, sessions[0].content.size)
        assertEquals("text", sessions[0].content[0].type)
        assertEquals("Hi there!", sessions[0].content[0].text)
        assertEquals("text", sessions[0].content[1].type)
        assertEquals("DONE", sessions[0].content[1].text)
    }

    @Test
    fun onToolUse() {
        val tool = LLMToolCall(
            name = "search",
            arguments = mapOf("query" to "Kokibot")
        )
        log.onToolUse("3", 1, tool)

        val sessions = log.get("3")
        assertEquals(1, sessions.size)
        assertEquals(Role.TOOL_USE, sessions[0].role)
        assertEquals(1, sessions[0].iteration)
        assertEquals(1, sessions[0].content.size)
        assertEquals("tool_use", sessions[0].content[0].type)
        assertEquals("search", sessions[0].content[0].name)
        assertEquals(tool.arguments, sessions[0].content[0].arguments as Map<*, *>)
    }

    @Test
    fun onToolResult() {
        val tool = LLMToolCall(
            name = "search",
            arguments = mapOf("query" to "Kokibot")
        )
        log.onToolResult("3", 1, tool, "Hello world")

        val sessions = log.get("3")
        assertEquals(1, sessions.size)
        assertEquals(Role.TOOL_RESULT, sessions[0].role)
        assertEquals(1, sessions[0].iteration)
        assertEquals(1, sessions[0].content.size)
        assertEquals("tool_result", sessions[0].content[0].type)
        assertEquals("Hello world", sessions[0].content[0].text)
        assertEquals(tool.id, sessions[0].content[0].id)
    }

    @Test
    fun `get - non existing id`() {
        val sessions = log.get("non-existing-id")
        assertEquals(0, sessions.size)
    }
}
