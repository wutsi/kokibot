package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.util.MapUtil
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class Bootstrap(
    val contextFactory: ContextFactory,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Bootstrap::class.java)
    }

    private lateinit var context: Context
    private lateinit var assistant: Assistant
    private lateinit var channels: MutableList<Channel>

    @PostConstruct
    fun init() {
        val home = System.getProperty("user.home") + "/kokibot"
        init(File(home))
    }

    @PreDestroy
    fun destroy() {
        assistant.destroy()
        context.destroy()
        channels.forEach { channel -> channel.destroy() }
    }

    internal fun init(home: File) {
        LOGGER.info("Initializing form $home")

        val config = loadConfig(File(getConfigDir(home), "settings.json"))
        this.context = contextFactory.create(home, config)
        this.assistant = Assistant()
        this.channels = mutableListOf()

        context.init(assistant, config)
        assistant.init(config, context)
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
