package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata

class CompactCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/compact",
            description = "Compact the assistant memory",
        )
    }

    override fun exec(input: Message, context: Context): String {
        val start = System.currentTimeMillis()
        context.memory.compact()
        val duration = (System.currentTimeMillis() - start) / 1000
        return "✓ Memory compacted successfully in ${duration}s"
    }
}
