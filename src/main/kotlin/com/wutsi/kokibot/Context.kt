package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import com.wutsi.kokibot.service.FileService
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.SkillParser
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

class Context(
    val home: File,
    val llm: LLM,

    val config: Map<*, *> = emptyMap<String, String>(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val skillRegistry: SkillRegistry = SkillRegistry(SkillParser()),
    val commandRegistry: CommandRegistry = CommandRegistry(),
    val channelRegistry: ChannelRegistry = ChannelRegistry(ChannelFactory()),
    val marketplaceRegistry: MarketplaceRegistry = MarketplaceRegistry(),
    val memory: Memory = Memory(),
    val dailyLog: DailyLog = DailyLog(),
    val fileService: FileService = FileService(),
    val jsonMapper: JsonMapper = JsonMapper(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Context::class.java)
    }

    private val channels: MutableList<Channel> = mutableListOf()

    fun destroy() {
        resources().forEach { resource -> resource.destroy() }
    }

    fun init(assistant: Assistant, config: Map<*, *>) {
        initChannels(config, assistant)
        initMarketplaces(config) // IMPORTANT: Before initSkills() because some skills may depend on marketplaces.
        initSkills()
        initTools()
        initLLM(config)
        initMemory(config)
        initCommands()
        initFileService()
    }

    fun health(): Health {
        val healths = resources().map { resource -> resource.health() }
        return Health(
            id = "context",
            up = healths.all { it.up },
            children = healths,
        )
    }

    fun resources(): List<Resource> {
        return channels +
            skillRegistry.all() +
            toolRegistry.all() +
            channelRegistry.all() +
            marketplaceRegistry.all() +
            listOf(llm, dailyLog, memory, fileService)
    }

    private fun initChannels(config: Map<*, *>, assistant: Assistant) {
        channelRegistry.init(config, this, assistant)
    }

    private fun initMarketplaces(config: Map<*, *>) {
        marketplaceRegistry.init(config, this)
    }

    private fun initLLM(config: Map<*, *>) {
        try {
            val root = MapUtil.toMap("llm", config)
                ?: throw ConfigurationException("LLM has invalid structure or missing")

            llm.init(root, this)
        } catch (ex: Exception) {
            LOGGER.warn("Failed to initialize the LLM - ${ex.message}")
        }
    }

    private fun initMemory(config: Map<*, *>) {
        val root = MapUtil.toMap("memory", config)
            ?: emptyMap<String, Any>()

        dailyLog.init(root, this)
        memory.init(root, this)
    }

    private fun initTools() {
        toolRegistry.init(this)
    }

    private fun initCommands() {
        commandRegistry.init(this)
    }

    private fun initSkills() {
        skillRegistry.init(this)
    }

    private fun initFileService() {
        fileService.init(emptyMap<String, Any>(), this)
    }
}
