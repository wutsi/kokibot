package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MarketplaceRegistry(private val finder: GitSkillFinder = GitSkillFinder()) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarketplaceRegistry::class.java)
    }

    private val marketplaces = mutableMapOf<String, Marketplace>()

    fun all(): List<Marketplace> {
        return marketplaces.values.toList()
    }

    fun init(config: Map<*, *>, context: Context) {
        val items = MapUtil.toList("marketplaces", config)
        items?.forEach { entry ->
            if (entry is Map<*, *>) {
                try {
                    initMarketplace(entry, context)
                } catch (ex: Exception) {
                    LOGGER.warn("Failed to initialize the marketplace - ${ex.message}")
                }
            }
        }
    }

    private fun initMarketplace(config: Map<*, *>, context: Context) {
        val marketplace = Marketplace(finder)
        marketplace.init(config, context)

        LOGGER.info("Marketplace: ${marketplace.id()}")
        register(marketplace)
    }

    fun destroy() {
        marketplaces.values.forEach { marketplace -> marketplace.destroy() }
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
