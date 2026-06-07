package com.wutsi.kokibot.tools

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMToolCall
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ToolTest {
    private class TestTool : Tool {
        override fun metadata(): ToolMetadata = ToolMetadata(
            name = "test_tool",
            description = "Test tool"
        )

        override fun exec(arguments: Map<*, *>): String = "result"

        override fun statusText(toolCalls: List<LLMToolCall>) = ""
    }

    @Test
    fun `id - should return tool id with prefix`() {
        val tool = TestTool()
        assertEquals("tool:test_tool", tool.id())
    }

    @Test
    fun `init - should be callable without error`() {
        val tool = TestTool()
        val context = Context(
            home = File("/tmp/test"),
            llm = mock<LLM>(),
        )

        tool.init(emptyMap<String, Any>(), context)
    }
}
