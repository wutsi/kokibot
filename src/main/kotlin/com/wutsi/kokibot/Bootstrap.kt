package com.wutsi.kokibot

import com.wutsi.kokibot.service.credential.CredentialService
import com.wutsi.kokibot.service.credential.CredentialServiceImpl
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * Assistant bootstrap
 */
class Bootstrap(
    val contextFactory: ContextFactory,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Bootstrap::class.java)
    }

    private lateinit var context: Context

    fun destroy() {
        LOGGER.info("Destroying Assistant: ${context.assistant.name}")

        context.destroy()
    }

    fun init(home: File) {
        LOGGER.info("... Initializing Assistant: @${home.name} .............................................")

        val config = loadConfig(File(getConfigDir(home), "settings.json"))
        val credentialService = loadCredentialService(home)
        this.context = contextFactory.create(home, config, credentialService)

        context.init(config)

        LOGGER.info("Initialization completed")
    }

    private fun loadCredentialService(home: File): CredentialService {
        val globalFile = File(home.parentFile.parentFile, "config/credential.json")
        val localFile = File(getConfigDir(home), "credential.json")
        return CredentialServiceImpl(globalFile, localFile)
    }

    fun getContext(): Context {
        return context
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun set(key: String, value: Any) {
        val dot = key.indexOf('.')
        if (dot < 0) throw ConfigurationException("Setting key must use the format <section>.<property> (e.g. assistant.max-iterations)")

        val section = key.substring(0, dot)
        val property = key.substring(dot + 1)

        // Apply live first — if it throws, the file is not written
        when (section) {
            "assistant" -> context.assistant.apply(property, value)
            "memory" -> context.memory.apply(property, value)
            "heartbeat" -> context.heartbeat.apply(property, value)
            "knowledge-base" -> context.knowledgeBase.apply(property, value)
            "marketplace" -> context.marketplaceRegistry.apply(property, value)
            else -> throw ConfigurationException("Unknown setting section: $section")
        }

        // instructions are persisted to ASSISTANT.md by Assistant.apply(); skip settings.json
        if ((section == "assistant" && property == "instructions") || section == "marketplace") return

        // Update the settings.json file
        val file = File(File(context.home, "config"), "settings.json")
        val rawConfig = JsonMapper().readValue(file, Map::class.java).toMutableMap()
        val sectionMap = rawConfig.getOrPut(section) { mutableMapOf<String, Any>() } as MutableMap<Any?, Any?>
        sectionMap[property] = value
        JsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, rawConfig)
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
