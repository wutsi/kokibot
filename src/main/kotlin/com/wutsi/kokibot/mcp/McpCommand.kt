package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import org.slf4j.LoggerFactory

class McpCommand : Command {
    companion object {
        const val NAME = "/mcp"
        private val LOGGER = LoggerFactory.getLogger(McpCommand::class.java)
    }

    override fun metadata(): CommandMetadata = CommandMetadata(
        name = NAME,
        description = """
            List available MCP servers or show details of a specific one.
            Usages:
             - `/mcp`: list all configured MCP servers with activation status
             - `/mcp [server-name]`: show details of a specific MCP server
        """.trimIndent(),
    )

    override fun exec(input: Message, context: Context): String {
        val name = input.text.trim().lowercase()
        return if (name.isEmpty()) list(context) else detail(name, context)
    }

    private fun list(context: Context): String {
        val servers = context.mcpRegistry.all().sortedBy { it.config.name }
        val lines = servers.joinToString("\n") { server ->
            val activated = context.activatedMcps.contains(server)
            "- ${server.config.name}: ${server.config.description} ${if (activated) "[activated]" else "[not activated]"}"
        }
        return "${servers.size} MCP server(s) found\n$lines"
    }

    private fun detail(name: String, context: Context): String {
        return try {
            val server = context.mcpRegistry.get(name)
            val config = server.config
            val activated = context.activatedMcps.contains(server)
            val status = if (activated) "active" else "inactive"
            "*MCP Server:* ${config.name}\n\n" +
                "*Description:* ${config.description}\n\n" +
                "*LINK:* ${config.url}\n\n" +
                "*Status:* $status"
        } catch (ex: McpNotFoundException) {
            LOGGER.warn("MCP server not found: $name", ex)
            "MCP server not found: `$name`"
        }
    }
}
