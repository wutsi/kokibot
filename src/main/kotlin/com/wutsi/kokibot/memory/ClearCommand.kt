package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata

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
        if (!input.trim().equals("confirm", ignoreCase = true)) {
            return """
                To clear the chat history, please use the command with the "confirm" parameter:
                  /clear confirm

                This is to avoid accidentally clearing the chat history, which cannot be undone.
            """.trimIndent()
        } else {
            context.chatHistory.clear()
            return "Chat history cleared"
        }
    }
}
