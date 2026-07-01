package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Registry
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ToolRegistry : Registry<Tool>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ToolRegistry::class.java)
    }

    override fun id() = "tool-registry"
    override fun keyOf(tool: Tool) = tool.metadata().name
    override fun notFound(name: String) = ToolNotFoundException("Tool not found: $name")
    override fun destroyItem(tool: Tool) = tool.destroy()

    override fun init(context: Context) {
        items.values.forEach { tool ->
            LOGGER.info("Tool: ${tool.metadata().name}")
            try {
                initTool(tool, context)
            } catch (e: Exception) {
                LOGGER.warn("Unable to initialize the Tool ${tool.metadata().name} - Error:" + e.message)
            }
        }
    }

    private fun initTool(tool: Tool, context: Context) {
        val dir = File(File(context.home, "config"), "tools")
        val file = File(dir, tool.metadata().name + ".json")
        if (file.exists()) {
            tool.init(loadConfig(file), context)
        } else {
            tool.init(emptyMap<String, Any>(), context)
        }
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }
}
