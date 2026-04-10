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
        if (!input.trim().equals("confirm", ignoreCase = true)) {
            return """
                To compact the memory, please use the command with the "confirm" parameter:
                  /compact confirm

                This is to avoid accidentally compacting the memory, which cannot be undone.
            """.trimIndent()
        } else {
            context.memory.compact()
            return "Memory compacted"
        }
    }
}
