package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata

// Stub implementation — Task 4 will provide the full implementation.
class McpTool(
    private val serverName: String,
    private val toolDef: McpToolDefinition,
    private val server: McpServer,
) : Tool {
    override fun metadata(): ToolMetadata = ToolMetadata(
        name = toolDef.name,
        description = toolDef.description ?: "",
    )

    override fun exec(arguments: Map<*, *>): String {
        return server.client.callTool(toolDef.name, arguments)
    }

    override fun statusText(toolCalls: List<LLMToolCall>): String = "Calling ${toolDef.name} on $serverName…"

    override fun id(): String = "tool:$serverName:${toolDef.name}"

    override fun init(config: Map<*, *>, context: Context) {}

    override fun health(): Health = Health(id = id(), up = true)
}
