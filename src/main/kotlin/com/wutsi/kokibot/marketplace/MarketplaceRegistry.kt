package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Registry
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class MarketplaceRegistry(private val finder: GitSkillFinder = GitSkillFinder()) : Registry<Marketplace>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarketplaceRegistry::class.java)
    }

    private lateinit var context: Context

    override fun id() = "marketplace-registry"
    override fun keyOf(marketplace: Marketplace) = marketplace.id()
    override fun notFound(name: String) = MarketplaceNotFoundException("Marketplace not found: $name")
    override fun destroyItem(marketplace: Marketplace) = marketplace.destroy()

    override fun init(context: Context) {
        this.context = context

        val dir = getMarketplaceDir()
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
        val config = JsonMapper().readValue(file, Map::class.java).toMutableMap()
        config["name"] = file.nameWithoutExtension
        return MapUtil.applyEnv(config)
    }

    private fun initMarketplace(config: Map<*, *>, context: Context) {
        val marketplace = Marketplace(finder)
        marketplace.init(config, context)
        LOGGER.info("Marketplace: ${marketplace.id()}")
        register(marketplace)
    }

    fun apply(key: String, value: Any) {
        val dot = key.indexOf('.')
        if (dot < 0) throw IllegalArgumentException("Marketplace property must use the format <marketplace>.<property> (e.g. my-marketplace.enabled)")

        val name = key.substring(0, dot)
        val property = key.substring(dot + 1)

        val marketplace = get("marketplace:${name.lowercase()}")
        marketplace.apply(property, value)

        val file = File(getMarketplaceDir(), "$name.json")
        file.parentFile.mkdirs()
        val config = JsonMapper().readValue(file, Map::class.java).toMutableMap()
        config[property] = value
        JsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, config)
    }

    private fun getMarketplaceDir(): File = File(context.home, "config/marketplaces")
}
