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
    private val disabledMarketplaces = mutableSetOf<String>()

    fun disabledMarketplaces(): Set<String> = disabledMarketplaces.toSet()

    fun isEnabled(marketplace: Marketplace): Boolean = marketplace.getName() !in disabledMarketplaces

    override fun id() = "marketplace-registry"
    override fun keyOf(marketplace: Marketplace) = marketplace.id()
    override fun notFound(name: String) = MarketplaceNotFoundException("Marketplace not found: $name")
    override fun destroyItem(marketplace: Marketplace) = marketplace.destroy()

    override fun init(context: Context) {
        this.context = context

        @Suppress("UNCHECKED_CAST")
        val disabled = MapUtil.toMap("marketplaces", context.config)?.get("disabled") as? List<String>
        disabled?.forEach { disabledMarketplaces.add(it) }

        // Global marketplaces: {kokibot-home}/config/marketplaces/ (loaded first so agent can override)
        initMarketplace(context, File(context.home.parentFile.parentFile, "config/marketplaces"))

        // Agent-local marketplaces: {agent-home}/config/marketplaces/
        initMarketplace(context, getMarketplaceDir())
    }

    private fun initMarketplace(context: Context, dir: File) {
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
        when (property) {
            "enabled" -> {
                val enabled = value.toString().toBoolean()
                if (enabled) {
                    disabledMarketplaces.remove(name.lowercase())
                    marketplace.getSkills().forEach { skill -> context.skillRegistry.register(skill) }
                } else {
                    disabledMarketplaces.add(name.lowercase())
                    marketplace.getSkills().forEach { skill -> context.skillRegistry.unregister(skill) }
                }
            }
        }
    }

    private fun getMarketplaceDir(): File = File(context.home, "config/marketplaces")
}
