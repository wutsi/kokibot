package com.wutsi.kokibot.tools.user

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserAskQuestionToolTest {
    private val context = Context(
        home = File("target"),
        llm = mock(),
        sessionLog = mock(),
    )
    private val tool = UserAskQuestionTool()

    @BeforeEach
    fun setUp() {
        tool.init(emptyMap<String, String>(), context)
    }

    @Test
    fun id() {
        assertEquals(UserAskQuestionTool.ID, tool.id())
        assertEquals("user_ask", tool.id())
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()

        assertEquals(UserAskQuestionTool.ID, meta.name)
        assertTrue(meta.description.isNotBlank())
        assertEquals(1, meta.parameters.size)

        val question = meta.parameters[0]
        assertEquals("question", question.name)
        assertEquals(ToolParameterType.STRING, question.type)
        assertTrue(question.required)
        assertTrue(question.description.isNotBlank())
    }

    @Test
    fun exec() {
        val ex = assertThrows<AskQuestionException> { tool.exec(mapOf("question" to "What is your name?")) }

        assertEquals("What is your name?", ex.question)
        assertEquals(ex.question, ex.message)
    }

    @Test
    fun `exec - non-string question is converted via toString`() {
        val ex = assertThrows<AskQuestionException> { tool.exec(mapOf("question" to 42)) }

        assertEquals("42", ex.question)
    }

    @Test
    fun `exec - missing question throws`() {
        val ex = assertThrows<IllegalArgumentException> {
            tool.exec(emptyMap<String, Any>())
        }
        assertTrue(ex.message?.contains("question") == true)
    }

    @Test
    fun `exec - null question throws`() {
        val ex = assertThrows<IllegalArgumentException> {
            tool.exec(mapOf("question" to null))
        }
        assertTrue(ex.message?.contains("question") == true)
    }

    @Test
    fun `exec - empty question throws`() {
        val ex = assertThrows<IllegalArgumentException> {
            tool.exec(mapOf("question" to ""))
        }
        assertTrue(ex.message?.contains("question") == true)
    }

    @Test
    fun statusText() {
        val result = tool.statusText(
            listOf(
                LLMToolCall(
                    name = UserAskQuestionTool.ID,
                    arguments = mapOf("question" to "What is your name?"),
                )
            )
        )

        assertEquals("Asking question to user", result)
    }

    @Test
    fun `statusText - empty tool calls`() {
        val result = tool.statusText(emptyList())

        assertEquals("Asking question to user", result)
    }

    @Test
    fun `statusText - multiple tool calls`() {
        val result = tool.statusText(
            listOf(
                LLMToolCall(name = UserAskQuestionTool.ID, arguments = mapOf("question" to "Q1")),
                LLMToolCall(name = UserAskQuestionTool.ID, arguments = mapOf("question" to "Q2")),
            )
        )

        assertEquals("Asking question to user", result)
    }
}
