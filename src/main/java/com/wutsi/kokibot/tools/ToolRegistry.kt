package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ToolNotFoundException
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ToolRegistry {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ToolRegistry::class.java)
    }

    private val tools = mutableMapOf<String, Tool>()

    fun all(): List<Tool> {
        return tools.values.toList()
    }

    fun init(context: Context) {
        tools.values.forEach { tool ->
            LOGGER.info("Tool: ${tool.metadata().name}")
            init(tool, context)
        }
    }

    fun destroy() {
        tools.values.forEach { tool -> tool.destroy() }
    }

    fun register(tool: Tool) {
        tools[tool.metadata().name.lowercase()] = tool
    }

    fun get(name: String): Tool {
        return tools[name.lowercase()]
            ?: throw ToolNotFoundException("Tool not found: $name")
    }

    private fun init(tool: Tool, context: Context) {
        val dir = File(getConfigDir(context.home), "tools")
        val file = File(dir, tool.metadata().name + ".json")
        if (file.exists()) {
            val config = loadConfig(file)
            tool.init(config, context)
        } else {
            tool.init(emptyMap<String, Any>(), context)
        }
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }
}
