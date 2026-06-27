package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class MarketplaceRegistry(private val finder: GitSkillFinder = GitSkillFinder()) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarketplaceRegistry::class.java)
    }

    private val marketplaces = mutableMapOf<String, Marketplace>()

    fun all(): List<Marketplace> {
        return marketplaces.values.toList()
    }

    fun init(context: Context) {
        val dir = File(context.home, "config/marketplaces")
        if (!dir.exists()) return

        dir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.forEach { file ->
                try {
                    val config = loadConfig(file)
                    initMarketplace(config, context)
                } catch (ex: Exception) {
                    LOGGER.warn("Failed to initialize the marketplace ${file.nameWithoutExtension} - ${ex.message}")
                }
            }
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun initMarketplace(config: Map<*, *>, context: Context) {
        val marketplace = Marketplace(finder)
        marketplace.init(config, context)

        LOGGER.info("Marketplace: ${marketplace.id()}")
        register(marketplace)
    }

    fun destroy() {
        marketplaces.values.forEach { marketplace ->
            try {
                marketplace.destroy()
            } catch (e: Exception) {
                LOGGER.warn("Failed to destroy the marketplace ${marketplace.id()} - ${e.message}")
            }
        }
        marketplaces.clear()
    }

    private fun register(marketplace: Marketplace) {
        marketplaces[marketplace.id().lowercase()] = marketplace
    }

    fun get(name: String): Marketplace {
        return marketplaces[name.lowercase()]
            ?: throw MarketplaceNotFoundException("Marketplace not found: $name")
    }
}
