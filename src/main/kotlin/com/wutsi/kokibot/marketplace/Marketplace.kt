package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillParser
import java.io.File

class Marketplace(private val skillFinder: GitSkillFinder = GitSkillFinder()) : Resource {
    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(Marketplace::class.java)
    }

    private lateinit var name: String
    private lateinit var repoUrl: String
    private var icon: String? = null
    private var description: String? = null
    private lateinit var context: Context
    private lateinit var skillWhitelist: List<String>
    private var skills: MutableList<Skill> = mutableListOf()

    override fun id(): String {
        return "marketplace:$name"
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.name = config["name"] as? String
            ?: throw ConfigurationException("Missing required config: name")

        this.repoUrl = config["repo-url"] as? String
            ?: throw ConfigurationException("Missing required config: repo-url")

        this.icon = config["icon"]?.toString()
        this.description = config["description"]?.toString()

        this.skillWhitelist = (config["skill-whitelist"] as? List<*>)?.mapNotNull { it?.toString()?.lowercase() }
            ?: emptyList()

        this.context = context
    }

    fun getName(): String {
        return name
    }

    fun getRepoUrl(): String {
        return repoUrl
    }

    fun getIcon(): String? {
        return icon
    }

    fun getDescription(): String? {
        return description
    }

    fun getSkills(): List<Skill> {
        if (skills.isEmpty()) {
            skills.addAll(loadSkills())
        }
        return skills
    }

    override fun destroy() {
        skills.forEach { skill -> skill.destroy() }
        skills.isEmpty()
        super.destroy()
    }

    private fun loadSkills(): List<Skill> {
        val parser = SkillParser()
        val baseDirectory = getBaseDir()
        val skills = skillFinder.find(repoUrl, baseDirectory).mapNotNull { md ->
            try {
                val pair = parser.parse(md)
                val basename = pair.first.name
                val meta = pair.first.copy(name = "$name/$basename")
                val body = pair.second
                if (acceptSkill(basename)) {
                    Skill(meta, body)
                } else {
                    null
                }
            } catch (ex: Exception) {
                LOGGER.warn("Failed to parse skill $md in marketplace $name", ex)
                null
            }
        }

        return skills
    }

    private fun acceptSkill(name: String): Boolean {
        return skillWhitelist.isEmpty() || skillWhitelist.contains(name.lowercase())
    }

    private fun getBaseDir(): File {
        return File("${context.home.absolutePath}/workspace/marketplaces/$name")
    }
}
