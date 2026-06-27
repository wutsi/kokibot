package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

class McpRegistry : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpRegistry::class.java)
    }

    private val servers = mutableMapOf<String, McpServer>()

    override fun id(): String = "mcp-registry"

    override fun init(config: Map<*, *>, context: Context) {
        val dir = File(context.home, "config/mcps")
        if (!dir.exists()) return

        dir.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                try {
                    val rawConfig = loadConfig(file)
                    val server = McpServer()
                    server.init(rawConfig, context)
                    register(server)
                    LOGGER.info("MCP: ${server.config.name}")
                } catch (e: Exception) {
                    LOGGER.warn("Unable to initialize MCP from ${file.name}: ${e.message}")
                }
            }
    }

    override fun health(): Health = Health(id = id(), up = true)

    override fun destroy() {
        servers.values.forEach { service ->
            try {
                service.destroy()
            } catch (e: Exception) {
                LOGGER.warn("Unable to destroy MCP ${service.config.name}: ${e.message}")
            }
        }
        servers.clear()
    }

    fun all(): List<McpServer> = servers.values.sortedBy { it.config.name }

    fun get(name: String): McpServer =
        servers[name.lowercase()] ?: throw McpNotFoundException("MCP server not found: $name")

    fun register(server: McpServer) {
        servers[server.config.name.lowercase()] = server
    }

    private fun loadConfig(file: File): Map<*, *> {
        val raw = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(raw)
    }
}
