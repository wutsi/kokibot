package com.wutsi.kokibot.skill

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class Skill(
    val metadata: SkillMetadata,
    val marketplace: String? = null,
) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Skill::class.java)
    }

    private val parser = SkillParser()
    private lateinit var context: Context

    val instructions: String
        get() {
            return parser.extractBody(File(metadata.home, "SKILL.md"))
        }

    override fun id(): String {
        return "skill:" + metadata.name
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun health(): Health {
        val missingEnv = missingEnv().ifEmpty { null }?.let { envs ->
            "- Missing environment variables: ${envs.joinToString(", ")}"
        }
        val missingBin = missingBinaries().ifEmpty { null }?.let { bins ->
            "- Missing binaries: ${bins.joinToString(", ")}. Please install them and restart the bot."
        }
        val osMismatch = if (osMatches()) {
            null
        } else {
            "- Expected OS: ${metadata.requiredOS}. Actual OS: ${this.getOS()}"
        }
        val up = missingEnv == null && missingBin == null && osMismatch == null

        return Health(
            id = id(),
            up = up,
            details = if (up) {
                null
            } else {
                listOfNotNull(missingEnv, missingBin, osMismatch).joinToString("\n")
            }
        )
    }

    private fun missingBinaries(): List<String> {
        return metadata.requiredBinaries.filter { bin -> !ShellUtil.exists(bin) }
    }

    private fun missingEnv(): List<String> {
        return metadata.requiredEnv.filter { env -> System.getenv(env) == null }
    }

    private fun osMatches(): Boolean {
        if (metadata.requiredOS.isEmpty()) {
            return true
        } else {
            val os = getOS()
            return metadata.requiredOS.find { it.equals(os, true) } != null
        }
    }

    private fun getOS(): String {
        return System.getProperty("os.name").lowercase()
    }

    fun activate(): Boolean {
        LOGGER.info("Activating Skill: ${metadata.name}")

        /* Make sure the skill is healthy */
        if (!health().up) {
            return false
        }

        /* Setup */
        if (metadata.requiredSetup.isNotEmpty()) {
            metadata.requiredSetup.forEach { cmd ->
                LOGGER.debug("... setup: $cmd")
                val result = ShellUtil.exec(cmd)
                if (result.status != 0) {
                    LOGGER.warn("Setup failed.\n$cmd\nError: ${result.error}")
                    return false
                }
            }
        }

        return true
    }

    @Synchronized
    fun apply(key: String, value: Any) {
        if (marketplace != null) {
            throw ConfigurationException("Cannot change skill settings for skills from a marketplace")
        }

        when (key) {
            "instructions" -> saveInstructions(value.toString())

            else -> throw ConfigurationException("Unknown assistant setting: $key")
        }
    }

    fun saveInstructions(instruction: String) {
        File(metadata.home, "SKILL.md").writeText(instruction)
    }
}
