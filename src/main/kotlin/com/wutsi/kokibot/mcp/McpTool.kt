package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class McpTool(
    private val serverName: String,
    private val toolDef: McpToolDefinition,
    private val server: McpServer,
) : Tool {
    override fun id(): String = "tool:${metadata().name}"

    override fun init(config: Map<*, *>, context: Context) {}

    override fun health(): Health = Health(id = id(), up = true)

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = toolName(serverName, toolDef.name),
        description = toolDef.description ?: "",
        parameters = toolDef.inputSchema.toToolParameters(),
    )

    override fun activate(): Boolean = server.activated

    override fun exec(arguments: Map<*, *>): String {
        if (!server.activated) return "MCP server `$serverName` is not activated. Call mcp_activate first."
        return try {
            server.client.callTool(toolDef.name, arguments)
        } catch (ex: Exception) {
            "Error calling MCP tool `${toolDef.name}`: ${ex.message}"
        }
    }

    override fun statusText(toolCalls: List<LLMToolCall>): String =
        "Calling MCP tool ${toolDef.name} on $serverName"
}

internal fun toolName(serverName: String, toolName: String): String =
    "${serverName.replace("-", "_").replace(" ", "_")}__$toolName"

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.toToolParameters(): List<ToolParameter> {
    val properties = this["properties"] as? Map<*, *> ?: return emptyList()
    val required = (this["required"] as? List<*>)?.map { it.toString() } ?: emptyList()
    return properties.map { (key, value) ->
        val propDef = value as? Map<*, *> ?: emptyMap<String, Any>()
        ToolParameter(
            name = key.toString(),
            type = when (propDef["type"]?.toString()) {
                "integer" -> ToolParameterType.INTEGER
                "number" -> ToolParameterType.NUMBER
                "boolean" -> ToolParameterType.BOOLEAN
                "array" -> ToolParameterType.ARRAY
                "object" -> ToolParameterType.OBJECT
                else -> ToolParameterType.STRING
            },
            description = propDef["description"]?.toString() ?: "",
            required = key.toString() in required,
        )
    }
}
