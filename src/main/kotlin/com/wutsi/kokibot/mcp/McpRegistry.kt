package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Registry
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

class McpRegistry : Registry<McpServer>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpRegistry::class.java)
    }

    override fun id() = "mcp-registry"
    override fun keyOf(server: McpServer) = server.config.name
    override fun notFound(name: String) = McpNotFoundException("MCP server not found: $name")
    override fun destroyItem(server: McpServer) = server.destroy()

    override fun init(context: Context) {
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

    private fun loadConfig(file: File): Map<*, *> {
        val raw = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(raw)
    }
}
