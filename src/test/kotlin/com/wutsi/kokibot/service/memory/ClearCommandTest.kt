package com.wutsi.kokibot.service.memory

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.llm.LLM
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
    val command = ClearCommand()

    @Test
    fun metadata() {
        val meta = command.metadata()
        assertEquals("/clear", meta.name)
    }

    @Test
    fun exec() {
        val input = Message(
            text = "",
            userId = "user-123",
            channelId = "channel-456",
        )
        command.exec(input, context)

        verify(chatHistory).clear(input.userId, input.channelId)
    }
}
