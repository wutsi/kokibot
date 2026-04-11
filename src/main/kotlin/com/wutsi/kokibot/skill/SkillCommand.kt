package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.util.MarkdownSanitizer
import org.slf4j.LoggerFactory

class SkillCommand : Command {
    companion object {
        const val NAME = "/skill"
        private val LOGGER = LoggerFactory.getLogger(SkillCommand::class.java)
    }

    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = NAME,
            description = """
                Return the list of available skills or the details of a specific tool.
                Usages:
                 - /skills: list all skill
                 - /skills [skill]: Show details of a specific skill,
            """.trimIndent(),
        )
    }

    override fun exec(input: String, context: Context): String {
        val name = input.trim().lowercase()
        return if (name.isEmpty()) {
            list(context)
        } else {
            skill(name, context)
        }
    }

    private fun skill(name: String, context: Context): String {
        try {
            val skill = context.skillRegistry.get(name)
            val meta = skill.metadata
            val tools = meta.tools.joinToString(separator = "\n") { tool -> "- ${sanitize(tool.name)}" }
            val requiredBin = meta.requiredBins.joinToString(separator = ",") { bin -> "- `${sanitize(bin)}`" }
            val requiredEnv = meta.requiredEnv.joinToString(separator = ",") { bin -> "- `${sanitize(bin)}`" }

            return """
                *Skill:* ${sanitize(meta.name)}

                *Description:* ${sanitize(meta.description ?: "N/A")}

                *Required Bin:* ${sanitize(requiredBin.ifEmpty { " N/A" })}

                *Required Env:* ${sanitize(requiredEnv.ifEmpty { " N/A" })}

                *Tools:*
            """.trimIndent() + (if (tools.isEmpty()) " N/A" else "\n$tools")
        } catch (ex: Exception) {
            LOGGER.warn("Unexpected error", ex)
            return "Tool not found: ${sanitize(name)}"
        }
    }

    private fun list(context: Context): String {
        val tools = context.skillRegistry.all()
        val result = "${tools.size} skill(s) found\n" +
            tools.joinToString(separator = "\n") { tool -> "- ${sanitize(tool.metadata.name)}" }

        return result
    }

    private fun sanitize(input: String): String {
        return MarkdownSanitizer.escape(input)
    }
}
