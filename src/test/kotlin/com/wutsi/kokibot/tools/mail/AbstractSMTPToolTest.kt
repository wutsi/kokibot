package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.tools.ToolRegistry
import java.io.File

abstract class AbstractSMTPToolTest : AbstractGreenMailTest() {
    protected val from: String = "no-reply@this-is-a-test.com"
    protected val context = Context(
        home = File("target/test-data/" + this::class.java.simpleName),
        llm = mock<LLM>(),
        toolRegistry = mock<ToolRegistry>(),
        chatHistory = mock<ChatHistory>(),
        memory = mock<Memory>(),
        config = mapOf(
            "mail" to mapOf(
                "smtp" to smtpConfig()
            )
        )
    )

    override fun port(): Int {
        return ServerSetupTest.SMTP.port
    }

    protected fun smtpConfig(): Map<String, Any> {
        return mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.SMTP.port,
            "username" to username,
            "password" to password,
            "from" to from,
        )
    }

    @Override
    override fun setup() {
        super.setup()
        context.smtp.init(smtpConfig(), context)
    }
}
