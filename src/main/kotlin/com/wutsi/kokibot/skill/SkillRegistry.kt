package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Registry
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File

class SkillRegistry(private val parser: SkillParser = SkillParser()) : Registry<Skill>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SkillRegistry::class.java)
        private val EMPTY_MAP = emptyMap<String, Any>()
    }

    private val disabledSkills = mutableSetOf<String>()

    fun disabledSkills(): Set<String> = disabledSkills.toSet()

    override fun id() = "skill-registry"
    override fun keyOf(skill: Skill) = skill.metadata.name
    override fun notFound(name: String) = SkillNotFoundException("Skill not found: $name")
    override fun destroyItem(skill: Skill) = skill.destroy()

    override fun init(context: Context) {
        @Suppress("UNCHECKED_CAST")
        val disabled = MapUtil.toMap("skills", context.config)?.get("disabled") as? List<String>
        disabled?.forEach { disabledSkills.add(it) }

        initSkills(context)
        initMarketplacesSkills(context)
    }

    fun isEnabled(skill: Skill): Boolean = skill.metadata.name !in disabledSkills

    private fun initSkills(context: Context) {
        // Global skills: {kokibot-home}/config/skills/ (loaded first so agent skills can override)
        initSkills(context, File(context.home.parentFile.parentFile, "config/skills"))

        // Agent-local skills: {agent-home}/config/skills/
        initSkills(context, File(context.home, "config/skills"))
    }

    private fun initSkills(context: Context, dir: File) {
        if (!dir.exists()) return

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                initSkill(file, context)
            }
        }
    }

    private fun initSkill(dir: File, context: Context) {
        try {
            val result = parser.parse(File(dir, "SKILL.md"))
            LOGGER.info("Skill: ${result.first.name}")
            val skill = Skill(result.first)
            register(skill)
            skill.init(EMPTY_MAP, context)
        } catch (ex: Exception) {
            LOGGER.warn("Unable to initialize the Skill ${dir.name} - Error:" + ex.message)
        }
    }

    fun apply(key: String, value: Any) {
        val dot = key.indexOf('.')
        if (dot < 0) throw IllegalArgumentException("Skill property must use the format <skill>.<property> (e.g. my-skill.enabled)")

        val name = key.substring(0, dot)
        val property = key.substring(dot + 1)

        when (property) {
            "enabled" -> if (value.toString().toBoolean()) disabledSkills.remove(name) else disabledSkills.add(name)
            else -> get(name).apply(property, value)
        }
    }

    private fun initMarketplacesSkills(context: Context) {
        context.marketplaceRegistry.all()
            .filter { context.marketplaceRegistry.isEnabled(it) }
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
