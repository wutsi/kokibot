package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

class ToolsCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/tools",
            description = "List all the available tools",
        )
    }

    override fun exec(input: String, context: Context): String {
        val tools = context.toolRegistry.all()
        val result = "${tools.size} tool(s) found\n" +
            tools.joinToString(separator = "\n") { tool -> "- ${tool.metadata().name}" }

        return result
    }
}
