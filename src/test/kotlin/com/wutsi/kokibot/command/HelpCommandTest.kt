package com.wutsi.kokibot.command

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class HelpCommandTest {
    private val commandRegistry = mock<CommandRegistry>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        commandRegistry = commandRegistry,
    )
    private val cmd = HelpCommand()

    @Test
    fun metadata() {
        assertEquals("/help", cmd.metadata().name)
    }

    @Test
    fun `exec list`() {
        val command1 = mock<Command>()
        val command2 = mock<Command>()
        doReturn(CommandMetadata(name = "command2")).whenever(command1).metadata()
        doReturn(CommandMetadata(name = "command1")).whenever(command2).metadata()
        doReturn(listOf(command1, command2)).whenever(commandRegistry).all()

        val result = cmd.exec(Message(text = ""), context)

        assertEquals(
            """
                2 command(s) found
                - command1
                - command2
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec command with parameter`() {
        val command = mock<Command>()
        doReturn(
            CommandMetadata(
                name = "command1",
                description = "Description of command1",
            )
        ).whenever(command).metadata()
        doReturn(command).whenever(commandRegistry).get(any())

        val result = cmd.exec(Message(text = "command1"), context)

        assertEquals(
            """
                command1

                Description of command1
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec bad command`() {
        doThrow(CommandNotFoundException::class).whenever(commandRegistry).get(any())

        val result = cmd.exec(Message(text = "command1"), context)

        assertEquals("Command not found: command1", result)
    }
}
