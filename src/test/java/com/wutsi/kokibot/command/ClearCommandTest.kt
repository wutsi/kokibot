package com.wutsi.kokibot.command

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ClearCommandTest {
    private val chatHistory = mock<ChatHistory>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        chatHistory = chatHistory,
    )
    private val cmd = ClearCommand()

    @Test
    fun metadata() {
        assertEquals("/clear", cmd.metadata().name)
    }

    @Test
    fun exec() {
        val result = cmd.exec(" CoNFiRm", context)

        verify(chatHistory).clear()
        assertEquals("Chat history cleared", result)
    }

    @Test
    fun `exec without confirmation`() {
        val result = cmd.exec("", context)

        assertEquals(
            """
                To clear the chat history, please use the command with the "confirm" parameter:
                  /clear confirm

                This is to avoid accidentally clearing the chat history, which cannot be undone.
            """.trimIndent(),
            result
        )

        verify(chatHistory, never()).clear()
    }
}
