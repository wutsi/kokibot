package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

class ClearCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/clear",
            description = """
                Clear the assistant chat history of the day.
                It does not affect the long term memory, but will make the assistant forget the context of the conversations of the current day
            """.trimIndent(),
        )
    }

    override fun exec(input: String, context: Context): String {
        context.chatHistory.clear()
        return "Chat history cleared"
    }
}
