package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.tools.ToolRegistry
import java.io.File

abstract class AbstractIMAPToolTest : AbstractGreenMailTest() {
    protected val context = Context(
        home = File("target/test-data/" + this::class.java.simpleName),
        llm = mock<LLM>(),
        toolRegistry = mock<ToolRegistry>(),
        chatHistory = mock<ChatHistory>(),
        config = imapConfig()
    )

    override fun port(): Int {
        return ServerSetupTest.IMAP.port
    }

    protected fun imapConfig(): Map<String, Any> {
        return mapOf(
            "mail" to mapOf(
                "imap" to mapOf(
                    "host" to "localhost",
                    "port" to ServerSetupTest.IMAP.port,
                    "username" to username,
                    "password" to password
                )
            )
        )
    }

    protected fun imapsConfig(): Map<String, Any> {
        return mapOf(
            "mail" to mapOf(
                "imaps" to mapOf(
                    "host" to "localhost",
                    "port" to ServerSetupTest.IMAPS.port,
                    "username" to username,
                    "password" to password
                )
            )
        )
    }
}
