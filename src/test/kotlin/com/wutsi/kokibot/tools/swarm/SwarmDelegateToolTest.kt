package com.wutsi.kokibot.tools.swarm

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.AssistantNotFoundException
import com.wutsi.kokibot.AssistantRegistry
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwarmDelegateToolTest {
    private val assistantRegistry = mock<AssistantRegistry>()
    private val context = Context(
        home = File("target"),
        llm = mock(),
        assistantRegistry = assistantRegistry,
    )
    private val tool = SwarmDelegateTool().also { it.init(emptyMap<String, String>(), context) }

    @Test
    fun id() {
        assertEquals(SwarmDelegateTool.ID, tool.id())
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(SwarmDelegateTool.ID, meta.name)
        assertTrue(meta.description.isNotBlank())
        assertTrue(meta.parameters.isNotEmpty())

        val name = meta.parameters.first { it.name == "name" }
        assertEquals(ToolParameterType.STRING, name.type)
        assertTrue(name.required)

        val task = meta.parameters.first { it.name == "task" }
        assertEquals(ToolParameterType.STRING, task.type)
        assertTrue(task.required)

        val context = meta.parameters.first { it.name == "context" }
        assertEquals(ToolParameterType.STRING, context.type)
        assertFalse(context.required)
    }

    @Test
    fun `exec delegates to assistant`() {
        // GIVEN
        val assistant = mock<Assistant>()
        doReturn(assistant).whenever(assistantRegistry).get("planner")
        doReturn(Message(text = "done", role = Role.ASSISTANT))
            .whenever(assistant).process(com.nhaarman.mockitokotlin2.any(), com.nhaarman.mockitokotlin2.anyOrNull())

        // WHEN
        val result = tool.exec(
            mapOf(
                "name" to "planner",
                "task" to "Plan my day",
                "context" to "Be brief",
            )
        )

        // THEN
        val msg = argumentCaptor<Message>()
        verify(assistant).process(msg.capture(), com.nhaarman.mockitokotlin2.anyOrNull())
        assertEquals(Role.USER, msg.firstValue.role)
        assertEquals("tool:${SwarmDelegateTool.ID}", msg.firstValue.userId)
        assertEquals("internal", msg.firstValue.channelId)
        assertTrue(msg.firstValue.text.contains("Plan my day"))
        assertTrue(msg.firstValue.text.contains("Be brief"))

        assertEquals("Result from `planner`:\ndone", result)
    }

    @Test
    fun `exec without optional context`() {
        // GIVEN
        val assistant = mock<Assistant>()
        doReturn(assistant).whenever(assistantRegistry).get("planner")
        doReturn(Message(text = "ok", role = Role.ASSISTANT))
            .whenever(assistant).process(com.nhaarman.mockitokotlin2.any(), com.nhaarman.mockitokotlin2.anyOrNull())

        // WHEN
        val result = tool.exec(mapOf("name" to "planner", "task" to "Plan"))

        // THEN
        val msg = argumentCaptor<Message>()
        verify(assistant).process(msg.capture(), com.nhaarman.mockitokotlin2.anyOrNull())
        assertEquals("Plan", msg.firstValue.text)
        assertEquals("Result from `planner`:\nok", result)
    }

    @Test
    fun `exec when assistant not found returns error message`() {
        // GIVEN
        doThrow(AssistantNotFoundException("not found")).whenever(assistantRegistry).get("ghost")

        // WHEN
        val result = tool.exec(mapOf("name" to "ghost", "task" to "Hello"))

        // THEN
        assertEquals(
            "Error: Specialist agent 'ghost' not found. Please check the name and try again.",
            result,
        )
    }

    @Test
    fun `exec when assistant throws returns error message`() {
        // GIVEN
        val assistant = mock<Assistant>()
        doReturn(assistant).whenever(assistantRegistry).get("planner")
        doThrow(RuntimeException("boom"))
            .whenever(assistant).process(com.nhaarman.mockitokotlin2.any(), com.nhaarman.mockitokotlin2.anyOrNull())

        // WHEN
        val result = tool.exec(mapOf("name" to "planner", "task" to "Hello"))

        // THEN
        assertEquals("Error delegating task to 'planner': boom", result)
    }

    @Test
    fun `exec missing name throws`() {
        assertThrows<IllegalArgumentException> {
            tool.exec(mapOf("task" to "Hello"))
        }
    }

    @Test
    fun `exec missing task throws`() {
        assertThrows<IllegalArgumentException> {
            tool.exec(mapOf("name" to "planner"))
        }
    }
}
