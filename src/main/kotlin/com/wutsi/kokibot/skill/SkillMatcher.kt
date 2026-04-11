package com.wutsi.kokibot.skill

import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory

class SkillMatcher {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SkillMatcher::class.java)
    }

    fun matches(input: String, skill: SkillMetadata): Boolean {
        val found = input.contains(skill.name, ignoreCase = true) ||
            skill.keywords.any { keyword -> input.contains(keyword, ignoreCase = true) } ||
            skill.categories.any { keyword -> input.contains(keyword, ignoreCase = true) }

        return found && checkDependencies(skill)
    }

    private fun checkDependencies(skill: SkillMetadata): Boolean {
        val binsExist = skill.requiredBins.all { ShellUtil.exists(it) }
        val envExists = skill.requiredEnv.all { System.getenv(it) != null }

        if (!binsExist || !envExists) {
            LOGGER.warn("Skill ${skill.name} skipped: Missing dependencies.")
            return false
        }
        return true
    }
}
