package com.wutsi.kokibot

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
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
) {
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
                val contextFactory = ContextFactory(jsonMapper = jsonMapper)
                val bootstrap = Bootstrap(contextFactory)
                bootstrap.init(dir)
                bootstraps.add(bootstrap)
            }
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
