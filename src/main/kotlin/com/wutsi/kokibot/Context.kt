package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import com.wutsi.kokibot.service.FileService
import com.wutsi.kokibot.service.heartbeat.Heartbeat
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.ConversationRepository
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.service.swarm.DelegationStack
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
    val assistant: Assistant = Assistant(),
    val config: Map<*, *> = emptyMap<String, String>(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val skillRegistry: SkillRegistry = SkillRegistry(SkillParser()),
    val commandRegistry: CommandRegistry = CommandRegistry(),
    val channelRegistry: ChannelRegistry = ChannelRegistry(ChannelFactory()),
    val marketplaceRegistry: MarketplaceRegistry = MarketplaceRegistry(),
    val memory: Memory = Memory(),
    val dailyLog: DailyLog = DailyLog(),
    val sessionLog: SessionLog = SessionLog(),
    val chatHistory: ChatHistory = ChatHistory(),
    val conversationRepository: ConversationRepository = ConversationRepository(),
    val fileService: FileService = FileService(),
    val heartbeat: Heartbeat = Heartbeat(),
    val delegationStack: DelegationStack = DelegationStack(),
    val jsonMapper: JsonMapper = JsonMapper(),
    val assistantRegistry: AssistantRegistry = AssistantRegistry(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Context::class.java)
    }

    private val channels: MutableList<Channel> = mutableListOf()

    fun destroy() {
        assistant.destroy()
        resources().forEach { resource -> resource.destroy() }
    }

    fun init(config: Map<*, *>) {
        initAssistant(config)
        initChannels(config)
        initMarketplaces(config) // IMPORTANT: Before initSkills() because some skills may depend on marketplaces.
        initSkills()
        initTools()
        initLLM(config)
        initMemory(config)
        initCommands()
        initFileService()
        initHeartbeat(config)
        initDelegationStack(config)
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
            listOf(llm, memory, dailyLog, sessionLog, chatHistory, conversationRepository, fileService, heartbeat, delegationStack)
    }

    private fun initAssistant(config: Map<*, *>) {
        assistant.init(
            MapUtil.toMap("assistant", config) ?: emptyMap<String, Any>(),
            this,
        )
    }

    private fun initChannels(config: Map<*, *>) {
        channelRegistry.init(config, this)
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

        memory.init(root, this)
        dailyLog.init(root, this)
        sessionLog.init(root, this)
        conversationRepository.init(root, this)
        chatHistory.init(root, this)
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

    private fun initHeartbeat(config: Map<*, *>) {
        heartbeat.init(
            MapUtil.toMap("heartbeat", config) ?: emptyMap<String, Any>(),
            this,
        )
    }

    private fun initDelegationStack(config: Map<*, *>) {
        delegationStack.init(
            MapUtil.toMap("swarm", config) ?: emptyMap<String, Any>(),
            this,
        )
    }
}
