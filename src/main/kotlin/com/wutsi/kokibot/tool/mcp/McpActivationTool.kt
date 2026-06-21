package com.wutsi.kokibot.tool.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.mcp.McpServer
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class McpActivationTool : Tool {
    companion object {
        const val NAME = "mcp_activate"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Activates an MCP server and makes its tools available. Call this before using any tools from an MCP server.",
        parameters = listOf(
            ToolParameter(
                name = "server",
                description = "Name of the MCP server to activate",
                type = ToolParameterType.STRING,
                required = true,
            ),
        ),
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val name = toolCalls.firstOrNull()?.arguments?.get("server")?.toString() ?: "MCP server"
        return "Activating MCP server: $name"
    }

    override fun exec(arguments: Map<*, *>): String {
        val name = arguments["server"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: server")
        return try {
            val server = context.mcpRegistry.get(name)
            server.initialize()
            if (!context.activatedMcps.contains(server)) context.activatedMcps.add(server)
            val tools = server.toolDefinitions.map { it.name }
            "Activated MCP server `$name`. ${if (tools.isEmpty()) "No tools available." else "Tools: ${tools.joinToString(", ")}. Use mcp_call to invoke them."}"
        } catch (ex: Exception) {
            "Unable to activate MCP server `$name`. Error: ${ex.message}"
        }
    }
}
