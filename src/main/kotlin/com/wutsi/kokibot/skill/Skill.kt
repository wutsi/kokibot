package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool
import org.slf4j.LoggerFactory

class Skill(val metadata: SkillMetadata) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Skill::class.java)
    }

    private lateinit var context: Context
    private lateinit var tools: List<Tool>

    fun init(context: Context) {
        this.context = context

        this.tools = metadata.tools.map { meta -> SkillTool(this, meta) }
        this.tools.forEach { tool ->
            LOGGER.info("....Skill tool: ${tool.metadata().name}")
            tool.init(emptyMap<String, Any>(), context)
        }
    }

    fun destroy() {
        this.tools.forEach { tool -> tool.destroy() }
    }

    fun getTools() = tools
}
