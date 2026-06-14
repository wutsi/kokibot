package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WebSearchToolTest {
    val tool = WebSearchTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(WebSearchTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)
        assertEquals("query", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `statusText - no tool calls`() {
        val result = tool.statusText(emptyList())
        assertEquals(true, result.contains("Searching online"))
    }

    @Test
    fun `statusText - single tool call`() {
        val toolCalls = listOf(
            LLMToolCall(name = WebSearchTool.NAME, arguments = mapOf("query" to "Capitale de la France"))
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Searching online: `Capitale de la France`", result)
    }

    @Test
    fun `statusText - multiple tool calls`() {
        val toolCalls = listOf(
            LLMToolCall(name = WebSearchTool.NAME, arguments = mapOf("query" to "Capitale de la France")),
            LLMToolCall(name = WebSearchTool.NAME, arguments = mapOf("query" to "Capital of Germany"))
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Searching online: `Capitale de la France`,`Capital of Germany`", result)
    }

    @Test
    fun exec() {
        val args = mapOf("query" to "Capitale de la France")
        val result = tool.exec(args)
        assertTrue(result.contains("Result #1"))
        assertTrue(result.contains("Paris"))
    }

    @Test
    fun `exec - empty command`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("query" to "")) }
    }

    @Test
    fun `exec - no command`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }
}
