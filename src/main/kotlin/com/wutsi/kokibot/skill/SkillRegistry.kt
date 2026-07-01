package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Registry
import org.slf4j.LoggerFactory
import java.io.File

class SkillRegistry(private val parser: SkillParser = SkillParser()) : Registry<Skill>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SkillRegistry::class.java)
        private val EMPTY_MAP = emptyMap<String, Any>()
    }

    override fun id() = "skill-registry"
    override fun keyOf(skill: Skill) = skill.metadata.name
    override fun notFound(name: String) = SkillNotFoundException("Skill not found: $name")
    override fun destroyItem(skill: Skill) = skill.destroy()

    override fun init(context: Context) {
        initSkills(context)
        initMarketplacesSkills(context)
    }

    private fun initSkills(context: Context) {
        val root = File(context.home, "config/skills")
        if (!root.exists()) return

        root.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                initSkill(file, context)
            }
        }
    }

    private fun initSkill(dir: File, context: Context) {
        try {
            val result = parser.parse(File(dir, "SKILL.md"))
            LOGGER.info("Skill: ${result.first.name}")
            val skill = Skill(metadata = result.first, body = result.second)
            register(skill)
            skill.init(EMPTY_MAP, context)
        } catch (ex: Exception) {
            LOGGER.warn("Unable to initialize the Skill ${dir.name} - Error:" + ex.message)
        }
    }

    private fun initMarketplacesSkills(context: Context) {
        context.marketplaceRegistry.all()
            .filter { it.isEnabled() }
            .forEach { marketplace ->
                try {
                    marketplace.getSkills().forEach { skill ->
                        LOGGER.info("Skill: ${skill.metadata.name}")
                        register(skill)
                        skill.init(EMPTY_MAP, context)
                    }
                } catch (ex: Exception) {
                    LOGGER.warn("Unable to initialize the Marketplace ${marketplace.id()}", ex)
                }
            }
    }
}
