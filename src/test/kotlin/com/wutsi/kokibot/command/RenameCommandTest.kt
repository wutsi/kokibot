package com.wutsi.kokibot.command

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.AssistantAlreadyRegisteredException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenameCommandTest {
    private val multiBootstrap = mock<MultiBootstrap>()
    private val cmd = RenameCommand(multiBootstrap)
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = Context(
            home = File("/tmp/agents/my-assistant"),
            llm = mock<LLM>(),
            assistant = Assistant("my-assistant"),
        )
    }

    @Test
    fun metadata() {
        assertEquals("/rename", cmd.metadata().name)
    }

    @Test
    fun `exec - success`() {
        val result = cmd.exec(Message(text = "new-assistant"), context)

        verify(multiBootstrap).rename("my-assistant", "new-assistant")
        assertEquals("✓ Assistant renamed from `my-assistant` to `new-assistant`", result)
    }

    @Test
    fun `exec - assistant already exists`() {
        doThrow(AssistantAlreadyRegisteredException("exists")).whenever(multiBootstrap)
            .rename("my-assistant", "existing")

        val result = cmd.exec(Message(text = "existing"), context)

        assertTrue(result.startsWith("Cannot rename"))
    }

    @Test
    fun `exec - rename failure`() {
        doThrow(RuntimeException("disk error")).whenever(multiBootstrap)
            .rename("my-assistant", "new-assistant")

        val result = cmd.exec(Message(text = "new-assistant"), context)

        assertTrue(result.startsWith("Rename failed"))
    }
}
