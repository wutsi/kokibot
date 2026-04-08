package com.wutsi.kokibot.tools

import com.wutsi.kokibot.exception.ToolNotFoundException
import org.springframework.stereotype.Service

@Service
class ToolRegistry {
    internal val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.metadata().name.lowercase()] = tool
    }

    fun get(name: String): Tool {
        return tools[name.lowercase()]
            ?: throw ToolNotFoundException("Tool not found: $name")
    }
}
