package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory

class Skill(val metadata: SkillMetadata) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Skill::class.java)
    }

    private lateinit var context: Context
    private lateinit var tools: List<Tool>

    override fun id(): String {
        return "skill:" + metadata.name
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context

        this.tools = metadata.tools.map { meta -> SkillTool(this, meta) }
        this.tools.forEach { tool ->
            LOGGER.info("....Skill tool: ${tool.metadata().name}")
            tool.init(emptyMap<String, Any>(), context)
        }
    }

    override fun destroy() {
        this.tools.forEach { tool -> tool.destroy() }
    }

    override fun health(): Health {
        val children = tools.map { tool -> tool.health() }

        val missingEnv = metadata.requiredEnv.filter { env -> System.getenv(env) == null }
            .map { env -> "- Environment variable `$env` is required but not set!" }

        val missingBin = metadata.requiredBins.filter { bin -> !ShellUtil.exists(bin) }
            .map { bin -> "- Binary `$bin` is required but not found in PATH!" }

        val details = missingEnv +
            missingBin +
            children.filter { health -> !health.up }.map { health -> "- ${health.details}" }

        return Health(
            id = id(),
            children = children,
            up = children.all { child -> child.up } && missingEnv.isEmpty() && missingBin.isEmpty(),
            details = details.joinToString("\n").ifEmpty { null }
        )
    }

    fun getTools() = tools
}
