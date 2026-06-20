package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.tools.ToolRegistry
import org.slf4j.LoggerFactory

class McpServer(
    val config: McpServerConfig,
    private val transport: McpHttpTransport = McpOkHttpTransport(),
) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpServer::class.java)
    }

    var activated: Boolean = false
        private set

    lateinit var client: McpClient
        private set

    override fun id(): String = "mcp:${config.name}"

    override fun init(config: Map<*, *>, context: Context) {}

    override fun health(): Health = Health(id = id(), up = true)

    @Synchronized
    fun activate(toolRegistry: ToolRegistry) {
        if (activated) return

        LOGGER.info("Activating MCP server: ${config.name}")
        client = McpClient(config.url, config.token, transport)
        client.initialize()
        val tools = client.listTools()
        tools.forEach { toolDef ->
            toolRegistry.register(McpTool(config.name, toolDef, this))
        }
        activated = true
    }
}
