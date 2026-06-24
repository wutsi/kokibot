package com.wutsi.kokibot

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
        this.context = contextFactory.create(home, config)

        context.init(config)

        LOGGER.info("Initialization completed")
    }

    fun getContext(): Context {
        return context
    }

    fun set(key: String, value: Any) {
        val file = File(File(context.home, "config"), "settings.json")

        @Suppress("UNCHECKED_CAST")
        val rawConfig = JsonMapper().readValue(file, Map::class.java).toMutableMap() as MutableMap<Any?, Any?>

        @Suppress("UNCHECKED_CAST")
        val assistantSection = rawConfig.getOrPut("assistant") { mutableMapOf<String, Any>() } as MutableMap<Any?, Any?>
        assistantSection[key] = value
        JsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, rawConfig)
        context.assistant.apply(key, value)
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
