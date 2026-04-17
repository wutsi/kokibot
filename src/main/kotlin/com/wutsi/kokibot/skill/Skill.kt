package com.wutsi.kokibot.skill

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class Skill(val metadata: SkillMetadata, val body: String) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Skill::class.java)
    }

    private lateinit var context: Context

    override fun id(): String {
        return "skill:" + metadata.name
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    override fun health(): Health {
        val missingEnv = missingEnv().ifEmpty { null }
        return Health(
            id = id(),
            up = missingEnv == null,
            details = missingEnv?.let { envs ->
                "Environment variable(s) is required but not set: ${envs.joinToString(",")}"
            }
        )
    }

    private fun missingBinaries(): List<String> {
        return metadata.requiredBinaries.filter { bin -> !ShellUtil.exists(bin) }
    }

    private fun missingEnv(): List<String> {
        return metadata.requiredEnv.filter { env -> System.getenv(env) == null }
    }

    fun activate(): Boolean {
        LOGGER.info("Activating Skill: ${metadata.name}")

        if (!canActivate()) {
            return false
        }

        /* Install missing dependencies */
        LOGGER.info("Setting up: ${metadata.name}")
        val dir = File(context.home.absolutePath + "/workspace")
        dir.mkdirs()
        metadata.requiredSetup.forEach { cmd ->
            val result = ShellUtil.exec(cmd, directory = dir, timeoutSeconds = 60)
            LOGGER.debug(result)
        }
        return true
    }

    private fun canActivate(): Boolean {
        return missingEnv().isEmpty()
    }
}
