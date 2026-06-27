package com.wutsi.kokibot

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files

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
                initBootstrap(dir)
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

    fun rename(fromName: String, toName: String) {
        val newName = toName.trim()
        if (newName == fromName) return

        // Make sure the new name is valid
        if (newName.any { it == '/' || it == '\\' || it.isWhitespace() } || newName.trim().isEmpty()) {
            throw IllegalArgumentException("Invalid name: must not contain path separators or whitespace")
        }

        // Make sure the new name is not already registered
        if (get(newName) != null) throw AssistantAlreadyRegisteredException("Assistant with name `$newName` already exists")

        // Unregister
        val bootstrap = get(fromName) ?: throw AssistantNotFoundException("Assistant with name `$fromName` not found")
        val home = bootstrap.getContext().home
        val newHome = File(home.parentFile, newName)

        bootstraps.remove(bootstrap)
        bootstrap.destroy()

        Files.move(home.toPath(), newHome.toPath())
        initBootstrap(newHome)
    }

    fun get(name: String): Bootstrap? {
        return bootstraps.find { bootstrap -> bootstrap.getContext().assistant.name == name }
    }

    private fun initBootstrap(dir: File) {
        val contextFactory = ContextFactory(
            jsonMapper = jsonMapper,
            assistantRegistry = assistantRegistry,
            multiBootstrap = this,
        )
        val bootstrap = Bootstrap(contextFactory)
        try {
            bootstrap.init(dir)
            bootstraps.add(bootstrap)
        } catch (ex: Exception) {
            LOGGER.warn("Could not initialize agent from $dir. ${ex.message}")
        }
    }
}
