package com.wutsi.kokibot.tools

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ToolNotFoundException
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ToolCommandTest {
    private val toolRegistry = mock<ToolRegistry>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        toolRegistry = toolRegistry,
    )
    private val cmd = ToolCommand()

    @Test
    fun metadata() {
        assertEquals(ToolCommand.NAME, cmd.metadata().name)
    }

    @Test
    fun `exec list`() {
        val tool1 = mock<Tool>()
        val tool2 = mock<Tool>()
        doReturn(ToolMetadata(name = "tool1")).whenever(tool1).metadata()
        doReturn(ToolMetadata(name = "tool2")).whenever(tool2).metadata()
        doReturn(listOf(tool1, tool2)).whenever(toolRegistry).all()

        val result = cmd.exec("", context)

        assertEquals(
            """
                2 tool(s) found
                - `tool1`
                - `tool2`
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec tool with parameter`() {
        val tool = mock<Tool>()
        doReturn(
            ToolMetadata(
                name = "tool_1",
                description = "description of tool1",
                parameters = listOf(
                    ToolParameter(
                        name = "p1",
                        description = "description of p1",
                        type = ToolParameterType.STRING,
                        required = true
                    ),
                    ToolParameter(
                        name = "p2",
                        description = "description of p2",
                        type = ToolParameterType.INTEGER,
                        required = false
                    )
                )
            )
        ).whenever(tool).metadata()
        doReturn(tool).whenever(toolRegistry).get(any())

        val result = cmd.exec("tool_1", context)

        assertEquals(
            """
                *Tool:* tool\_1

                *Description:*
                description of tool1

                *Parameters:*
                - `p1`:`STRING` \[required] description of p1
                - `p2`:`INTEGER` description of p2
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec tool without parameters`() {
        val tool = mock<Tool>()
        doReturn(
            ToolMetadata(
                name = "tool1",
                description = "description of tool1",
            )
        ).whenever(tool).metadata()
        doReturn(tool).whenever(toolRegistry).get(any())

        val result = cmd.exec("tool1", context)

        assertEquals(
            """
                *Tool:* tool1

                *Description:*
                description of tool1

                *Parameters:*
                N/A
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec bad tool`() {
        doThrow(ToolNotFoundException::class).whenever(toolRegistry).get(any())

        val result = cmd.exec("tool1", context)

        assertEquals("Tool not found: `tool1`", result)
    }
}
