package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

class CompactCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/compact",
            description = "Compact the assistant memory",
        )
    }

    override fun exec(input: String, context: Context): String {
        context.memory.compact()
        return "Memory compacted"
    }
}
