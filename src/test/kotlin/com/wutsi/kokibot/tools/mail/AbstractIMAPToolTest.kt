package com.wutsi.kokibot.tools.mail

import com.icegreen.greenmail.util.ServerSetupTest
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.mail.IMAP
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.tools.ToolRegistry
import java.io.File

abstract class AbstractIMAPToolTest : AbstractGreenMailTest() {
    protected val context = Context(
        home = File("target/test-data/" + this::class.java.simpleName),
        llm = mock<LLM>(),
        toolRegistry = mock<ToolRegistry>(),
        chatHistory = mock<ChatHistory>(),
        memory = mock<Memory>(),
        imap = IMAP(),
        config = mapOf(
            "mail" to mapOf(
                "imap" to imapConfig()
            )
        )
    )

    override fun port(): Int {
        return ServerSetupTest.IMAP.port
    }

    protected fun imapConfig(): Map<String, Any> {
        return mapOf(
            "host" to "localhost",
            "port" to ServerSetupTest.IMAP.port,
            "username" to username,
            "password" to password
        )
    }

    @Override
    override fun setup() {
        super.setup()
        context.imap.init(imapConfig(), context)
    }
}
