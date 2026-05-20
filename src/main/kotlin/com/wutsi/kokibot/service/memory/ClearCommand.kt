package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata

class ClearCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/clear",
            description = "Empty the memory of the current conversation",
        )
    }

    override fun exec(input: Message, context: Context): String {
        context.chatHistory.clear(input.userId, input.channelId)
        return "✓ Chat history has been cleared"
    }
}
