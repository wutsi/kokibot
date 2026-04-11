package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.util.ShellUtil
import java.io.File

class SkillTool(
    private val skill: Skill,
    private val metadata: ToolMetadata,
) : Tool {
    private lateinit var context: Context

    override fun metadata(): ToolMetadata = metadata

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun exec(arguments: Map<*, *>): String {
        return sh(arguments)
            ?: py(arguments)
            ?: "Sorry, I cannot execute the tool `${metadata.name}`. No script found!\n"
    }

    private fun sh(arguments: Map<*, *>): String? {
        val line = metadata().parameters
            .mapNotNull { parameter -> arguments[parameter.name]?.toString() }
            .joinToString(separator = " ")

        val script = getScript("sh")
        return if (script.exists()) {
            ShellUtil.exec("${script.absolutePath} $line", directory = script.parentFile)
        } else {
            null
        }
    }

    private fun py(arguments: Map<*, *>): String? {
        val json = context.jsonMapper.writeValueAsString(arguments)
        val script = getScript("py")
        return if (script.exists()) {
            ShellUtil.exec("python3 ${script.absolutePath} \"$json\"", directory = script.parentFile)
        } else {
            null
        }
    }

    private fun getScript(extension: String): File {
        return File(context.home.absolutePath + "/skills/${skill.metadata.name}/scripts/${metadata.name}.$extension")
    }
}
