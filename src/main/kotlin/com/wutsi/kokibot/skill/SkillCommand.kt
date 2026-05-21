package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.util.MarkdownUtil
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
                Return the list of available skills or the details of a specific skill.
                Usages:
                 - `/skill`: list all skill
                 - `/skill [skill]`: Show details of a specific skill
            """.trimIndent(),
        )
    }

    override fun exec(input: Message, context: Context): String {
        val name = input.text.trim().lowercase()
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
            val requiredBin =
                meta.requiredBinaries.joinToString(separator = ", ") { bin -> "`$bin`" }.ifEmpty { "None" }
            val requiredEnv = meta.requiredEnv.joinToString(separator = ", ") { env -> "`$env`" }.ifEmpty { "None" }

            return "*Skill:* ${sanitize(meta.name)}\n\n" +
                "*Description:*\n${sanitize(meta.description)}\n\n" +
                "*Required Bin:* $requiredBin\n\n" +
                "*Required Env:* $requiredEnv"
        } catch (ex: Exception) {
            LOGGER.warn("Unexpected error", ex)
            return "Skill not found: `$name`"
        }
    }

    private fun list(context: Context): String {
        val skill = context.skillRegistry.all()
            .sortedBy { skill -> skill.metadata.name }

        val result = "${skill.size} skill(s) found\n" +
            skill.joinToString(separator = "\n") { tool -> "- ${sanitize(tool.metadata.name)}" }

        return result
    }

    private fun sanitize(input: String): String {
        return MarkdownUtil.escape(input)
    }
}
