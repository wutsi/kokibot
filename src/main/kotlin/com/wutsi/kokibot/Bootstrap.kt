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

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
