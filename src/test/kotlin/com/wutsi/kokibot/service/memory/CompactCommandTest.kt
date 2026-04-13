package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.memory.CompactCommand
import com.wutsi.kokibot.service.memory.Memory
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class CompactCommandTest {
    private val memory = mock<Memory>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        memory = memory,
    )
    private val cmd = CompactCommand()

    @Test
    fun metadata() {
        assertEquals("/compact", cmd.metadata().name)
    }

    @Test
    fun exec() {
        val result = cmd.exec(" CoNfIrM ", context)

        verify(memory).compact()
        assertEquals("Memory compacted", result)
    }

    @Test
    fun `exec without confirmation`() {
        val result = cmd.exec("", context)

        assertEquals(
            """
                To compact the memory, please use the command with the "confirm" parameter:
                  /compact confirm

                This is to avoid accidentally compacting the memory, which cannot be undone.
            """.trimIndent(),
            result
        )

        verify(memory, never()).compact()
    }
}
