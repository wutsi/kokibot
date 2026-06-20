package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import org.slf4j.LoggerFactory

class McpServer(
    val config: McpServerConfig,
    private val transport: McpHttpTransport = McpOkHttpTransport(),
) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpServer::class.java)
    }

    var toolDefinitions: List<McpToolDefinition> = emptyList()
        private set

    lateinit var client: McpClient
        private set

    override fun id(): String = "mcp:${config.name}"

    override fun init(config: Map<*, *>, context: Context) {}

    override fun health(): Health = Health(id = id(), up = true)

    @Synchronized
    fun initialize() {
        if (::client.isInitialized) return
        LOGGER.info("Initializing MCP server: ${config.name}")
        client = McpClient(config.url, config.token, transport)
        client.initialize()
        toolDefinitions = client.listTools()
    }
}
