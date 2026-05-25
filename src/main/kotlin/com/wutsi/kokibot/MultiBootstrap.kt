package com.wutsi.kokibot

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * Multi-Assistant bootstrap
 */
@Service
class MultiBootstrap(
    private val env: Environment,
    private val jsonMapper: JsonMapper,
    private val assistantRegistry: AssistantRegistry,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MultiBootstrap::class.java)
    }

    val bootstraps = mutableListOf<Bootstrap>()

    @PostConstruct
    fun init() {
        val profiles = env.activeProfiles.joinToString(", ")
        val home = when {
            profiles.contains("prod") -> System.getProperty("user.home") + "/.kokibot"
            else -> System.getProperty("user.home") + "/kokibot"
        }

        init(File(home))
    }

    internal fun init(home: File) {
        val agents = File(home, "agents")
        if (agents.exists()) {
            agents.listFiles { file -> file.isDirectory }?.forEach { dir ->
                val contextFactory = ContextFactory(
                    jsonMapper = jsonMapper,
                    assistantRegistry = assistantRegistry,
                )
                val bootstrap = Bootstrap(contextFactory)
                try {
                    bootstrap.init(dir)
                    bootstraps.add(bootstrap)
                } catch (ex: Exception) {
                    LOGGER.warn("Could not initialize agent from $home. ${ex.message}")
                }
            }
        } else {
            LOGGER.warn("No agents/ directory - No assistant be loaded")
        }
    }

    @PreDestroy
    fun destroy() {
        bootstraps.forEach { b ->
            b.destroy()
        }
        bootstraps.clear()
    }
}
