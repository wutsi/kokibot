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
        val globalFile = File(home.parentFile.parentFile, "config/credentials.json")
        val localFile = File(getConfigDir(home), "credentials.json")
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
            "llm" -> context.llm.apply(property, value)
            "marketplace" -> context.marketplaceRegistry.apply(property, value)
            "skill" -> context.skillRegistry.apply(property, value)
            else -> throw ConfigurationException("Unknown setting section: $section")
        }

        if (
            (section == "assistant" && property == "instructions") ||
            (section == "heartbeat" && property == "instructions") ||
            section == "skill" ||
            section == "marketplace"
        ) {
            return
        }

        // Update the settings.json file
        updateSettings(section, listOf(Pair(property, value)))
    }

    fun changeLLM(name: String, model: String) {
        // Validate
        val llm = contextFactory.llmFactory.create(name)
        if (!llm.availableModels().contains(model)) {
            throw ConfigurationException("Model $model is not available for LLM $name")
        }

        // Update config
        updateSettings("llm", listOf(Pair("name", name), Pair("model", model)))

        // Reload
        destroy()
        init(context.home)
    }

    private fun updateSettings(section: String, values: List<Pair<String, Any>>) {
        val jsonMapper = context.jsonMapper
        val file = File(File(context.home, "config"), "settings.json")
        val rawConfig = jsonMapper.readValue(file, Map::class.java).toMutableMap()
        val sectionMap = rawConfig.getOrPut(section) { mutableMapOf<String, Any>() } as MutableMap<Any?, Any?>

        values.forEach { value ->
            sectionMap[value.first] = value.second
        }
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, rawConfig)
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
