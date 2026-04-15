package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.command.HealthCommand
import com.wutsi.kokibot.service.heartbeat.Heartbeat
import com.wutsi.kokibot.util.MapUtil
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class Bootstrap(
    val contextFactory: ContextFactory,
    val env: Environment,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Bootstrap::class.java)
    }

    private lateinit var context: Context
    private lateinit var assistant: Assistant
    private lateinit var heartbeat: Heartbeat
    private val channels: MutableList<Channel> = mutableListOf()

    @PostConstruct
    fun init() {
        val profiles = env.activeProfiles.joinToString(", ")
        val home = when {
            profiles.contains("prod") -> System.getProperty("user.home") + "/.kokibot"
            else -> System.getProperty("user.home") + "/kokibot"
        }

        init(File(home))
    }

    @PreDestroy
    fun destroy() {
        assistant.destroy()
        context.destroy()
        heartbeat.destroy()
        channels.forEach { channel -> channel.destroy() }
        channels.clear()
    }

    internal fun init(home: File) {
        LOGGER.info("Initializing form $home")

        val config = loadConfig(File(getConfigDir(home), "settings.json"))
        this.context = contextFactory.create(home, config)
        this.assistant = Assistant()

        context.init(assistant, config)
        assistant.init(
            MapUtil.toMap("assistant", config) ?: emptyMap<String, Any>(),
            context,
        )

        heartbeat = Heartbeat(assistant)
        heartbeat.init(
            MapUtil.toMap("heartbeat", config) ?: emptyMap<String, Any>(),
            context,
        )

        LOGGER.info("Checking health...")
        LOGGER.info(HealthCommand().exec("", context))

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
