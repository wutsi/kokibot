package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.service.heartbeat.Heartbeat
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
    private lateinit var heartbeat: Heartbeat
    private val channels: MutableList<Channel> = mutableListOf()

    fun destroy() {
        LOGGER.info("Destroying Assistant: ${context.assistant.name}")

        context.destroy()
        heartbeat.destroy()
        channels.forEach { channel -> channel.destroy() }
        channels.clear()
    }

    fun init(home: File) {
        LOGGER.info("... Initializing Assistant: @${home.name} .............................................")

        val config = loadConfig(File(getConfigDir(home), "settings.json"))
        this.context = contextFactory.create(home, config)

        context.init(config)

        heartbeat = Heartbeat(context.assistant)
        heartbeat.init(
            MapUtil.toMap("heartbeat", config) ?: emptyMap<String, Any>(),
            context,
        )

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
