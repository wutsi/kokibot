package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.util.MarkdownSanitizer
import org.slf4j.LoggerFactory

class HelpCommand : Command {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HelpCommand::class.java)
    }

    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/help",
            description = """
                Return the list of available commands or the details of a specific command.
                Usages:
                 - /help: list all commands
                 - /help [command]: Show details of a specific command",
            """.trimIndent()
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
            val command = context.commandRegistry.get(name)
            val meta = command.metadata()

            return """
                *Command:* ${sanitize(meta.name)}

                *Description:* ${sanitize(meta.description)}
            """.trimIndent()
        } catch (ex: Exception) {
            LOGGER.warn("Unexpected error", ex)
            return "Command not found: ${sanitize(name)}"
        }
    }

    private fun list(context: Context): String {
        val tools = context.commandRegistry.all()
        val result = "${tools.size} command(s) found\n" +
            tools.joinToString(separator = "\n") { tool -> "- ${sanitize(tool.metadata().name)}" }

        return result
    }

    private fun sanitize(input: String): String {
        return MarkdownSanitizer.escape(input)
    }
}
