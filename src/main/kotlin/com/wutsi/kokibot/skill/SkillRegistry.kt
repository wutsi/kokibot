package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.SkillNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

@Service
class SkillRegistry(private val parser: SkillParser) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SkillRegistry::class.java)
    }

    private val skills = mutableMapOf<String, Skill>()

    fun init(context: Context) {
        val root = File(context.home, "skills")
        if (root.exists()) {
            root.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    init(file, context)
                }
            }
        }
    }

    private fun init(dir: File, context: Context) {
        val md = File(dir, "SKILL.md")
        try {
            val result = parser.parse(md)
            val meta = result.first
            val body = result.second
            LOGGER.info("Skill: ${meta.name}")

            val skill = Skill(metadata = meta, body = body)
            register(skill)
            skill.init(emptyMap<String, Any>(), context)
        } catch (ex: Exception) {
            LOGGER.warn("Unable to initialize the Skill ${dir.name} - Error:" + ex.message)
        }
    }

    fun destroy() {
        skills.values.forEach { it.destroy() }
    }

    fun all(): List<Skill> {
        return skills.values.toList()
    }

    fun register(skill: Skill) {
        val name = skill.metadata.name.lowercase()
        skills[name] = skill
    }

    fun get(name: String): Skill {
        return skills[name.lowercase()]
            ?: throw SkillNotFoundException("Skill not found: $name")
    }
}
