package com.wutsi.kokibot.tools.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class SkillActivationTool : Tool {
    companion object {
        const val NAME = "skill_activate"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "This tool is used internally to activate skills. It fetches the content of the skill and add it into the context so that the assistant can read it and use it.",
        parameters = listOf(
            ToolParameter(
                name = "skills",
                description = "Name of the skill(s) to activate. For multiple skills, separate them with comma. Example: `weather, news`",
                type = ToolParameterType.STRING,
                required = true
            )
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val names = arguments["skills"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: name")

        return names.split(",")
            .joinToString("\n\n") { name ->
                activate(name.trim())
            }
    }

    private fun activate(name: String): String {
        try {
            val skill = context.skillRegistry.get(name)
            if (!skill.activate()) {
                return "Skill `$name` is not available now and cannot be activated."
            }

            val sb = StringBuilder()
            sb.append("\n\nSkill: ${skill.metadata.name}\n")
            sb.append("Here is the description of the skill in markdown format:\n")
            sb.append("```markdown\n${skill.body}\n```\n")
            return sb.toString()
        } catch (ex: Exception) {
            return "Unable to activate skill `$name`. Error= ${ex.message}"
        }
    }
}
