package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command

/**
 * Return the list of tools used by the assistant
 */
class ToolsCommand : Command {
    override fun name() = "/tools"

    override fun exec(input: String, context: Context): String {
        val tools = context.toolRegistry.all()
        return "${tools.size} tool(s) found\n" +
            tools.joinToString(separator = "\n") { tool -> "- ${tool.metadata().name}" }
    }
}
