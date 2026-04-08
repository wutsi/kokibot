package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.tools.ToolRegistry
import java.io.File

abstract class AbstractSMTPToolTest : AbstractGreenMailTest() {
    protected val from: String = "no-reply@this-is-a-test.com"
    protected val context = Context(
        home = File("target/test-data/" + this::class.java.simpleName),
        llm = mock<LLM>(),
        toolRegistry = mock<ToolRegistry>(),
        chatHistory = mock<ChatHistory>(),
        config = smtpConfig()
    )

    override fun port(): Int {
        return ServerSetupTest.SMTP.port
    }

    protected fun smtpsConfig(): Map<String, Any> {
        return mapOf(
            "mail" to mapOf(
                "smtps" to mapOf(
                    "host" to "localhost",
                    "port" to ServerSetupTest.SMTPS.port,
                    "username" to username,
                    "password" to password,
                    "from" to from,
                )
            )
        )
    }

    protected fun smtpConfig(): Map<String, Any> {
        return mapOf(
            "mail" to mapOf(
                "smtp" to mapOf(
                    "host" to "localhost",
                    "port" to ServerSetupTest.SMTP.port,
                    "username" to username,
                    "password" to password,
                    "from" to from,
                )
            )
        )
    }
}
