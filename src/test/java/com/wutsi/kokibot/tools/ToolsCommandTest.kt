package com.wutsi.kokibot.tools

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ToolsCommandTest {
    private val toolReegistry = mock<ToolRegistry>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        toolRegistry = toolReegistry,
    )
    private val cmd = ToolsCommand()

    @Test
    fun name() {
        assertEquals("/tools", cmd.name())
    }

    @Test
    fun exec() {
        val tool1 = mock<Tool>()
        val tool2 = mock<Tool>()
        doReturn(ToolMetadata(name = "tool1")).whenever(tool1).metadata()
        doReturn(ToolMetadata(name = "tool2")).whenever(tool2).metadata()
        doReturn(listOf(tool1, tool2)).whenever(toolReegistry).all()

        val result = cmd.exec("", context)

        assertEquals(
            """
                2 tool(s) found
                - tool1
                - tool2
            """.trimIndent(),
            result
        )
    }
}
