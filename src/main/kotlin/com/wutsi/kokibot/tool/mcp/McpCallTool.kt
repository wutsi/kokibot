package com.wutsi.kokibot.tool.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class McpCallTool : Tool {
    companion object {
        const val NAME = "mcp_call"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata() = ToolMetadata(
        name = NAME,
        description = "Call a tool on an activated MCP server.",
        parameters = listOf(
            ToolParameter("server", ToolParameterType.STRING, "Name of the activated MCP server", required = true),
            ToolParameter("tool", ToolParameterType.STRING, "Name of the tool to call on the MCP server", required = true),
            ToolParameter("arguments", ToolParameterType.OBJECT, "Arguments to pass to the tool", required = false),
        ),
    )

    override fun activate(): Boolean = context.activatedMcps.isNotEmpty()

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val server = toolCalls.firstOrNull()?.arguments?.get("server") ?: "?"
        val tool = toolCalls.firstOrNull()?.arguments?.get("tool") ?: "?"
        return "Calling MCP tool $tool on $server"
    }

    override fun exec(arguments: Map<*, *>): String {
        val serverName = arguments["server"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: server")
        val toolName = arguments["tool"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: tool")
        val toolArgs = arguments["arguments"] as? Map<*, *> ?: emptyMap<String, Any>()

        return try {
            val server = context.mcpRegistry.get(serverName)
            if (!context.activatedMcps.contains(server)) {
                return "MCP server `$serverName` is not activated. Call mcp_activate first."
            }
            server.client.callTool(toolName, toolArgs)
        } catch (ex: Exception) {
            "Error calling tool `$toolName` on `$serverName`: ${ex.message}"
        }
    }
}
