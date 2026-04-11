package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.util.MarkdownSanitizer
import org.slf4j.LoggerFactory

class ToolCommand : Command {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ToolCommand::class.java)
        const val NAME = "/tools"
    }

    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = NAME,
            description = """
                Return the list of available tools or the details of a specific tool.
                Usages:
                 - /tools: list all tools
                 - /tools [tool]: Show details of a specific tool,
            """.trimIndent(),
        )
    }

    override fun exec(input: String, context: Context): String {
        val name = input.trim().lowercase()
        return if (name.isEmpty()) {
            list(context)
        } else {
            tool(name, context)
        }
    }

    private fun tool(name: String, context: Context): String {
        try {
            val tool = context.toolRegistry.get(name)
            val meta = tool.metadata()
            val params = meta.parameters.joinToString(separator = "\n") { param ->
                "- `${sanitize(param.name)}`:`${param.type}`" +
                    (if (param.required) " \\[required\\]" else "") +
                    " " + sanitize(param.description)
            }

            return """
                *Tool:* ${sanitize(meta.name)}

                *Description:* ${sanitize(meta.description)}

                *Parameters:*
            """.trimIndent() + (if (params.isEmpty()) " N/A" else "\n$params")
        } catch (ex: Exception) {
            LOGGER.warn("Unexpected error", ex)
            return "Tool not found: ${sanitize(name)}"
        }
    }

    private fun list(context: Context): String {
        val tools = context.toolRegistry.all()
        val result = "${tools.size} tool(s) found\n" +
            tools.joinToString(separator = "\n") { tool -> "- ${sanitize(tool.metadata().name)}" }

        return result
    }

    private fun sanitize(input: String): String {
        return MarkdownSanitizer.escape(input)
    }
}
