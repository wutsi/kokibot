package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.llm.LLM
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
        val result = cmd.exec(Message(text = ""), context)

        verify(memory).compact()
        assertEquals(true, result.contains("Memory compacted"))
    }
}
