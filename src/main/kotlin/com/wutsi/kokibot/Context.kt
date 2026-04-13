package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.mail.IMAP
import com.wutsi.kokibot.mail.SMTP
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.memory.Memory
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
    val chatHistory: ChatHistory = ChatHistory(),
    val skillRegistry: SkillRegistry = SkillRegistry(SkillParser()),
    val commandRegistry: CommandRegistry = CommandRegistry(),
    val channelFactory: ChannelFactory = ChannelFactory(),
    val memory: Memory = Memory(),
    val smtp: SMTP = SMTP(),
    val imap: IMAP = IMAP(),
    val jsonMapper: JsonMapper = JsonMapper(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Context::class.java)
    }

    private val channels: MutableList<Channel> = mutableListOf()

    fun destroy() {
        resources().forEach { resource -> resource.destroy() }
        channels.clear()
    }

    fun init(assistant: Assistant, config: Map<*, *>) {
        initChannels(assistant, config)
        initSkills()
        initTools()
        initLLM(config)
        initMail(config)
        initMemory(config)
        initCommands()
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
            listOf(llm, imap, smtp, chatHistory, memory)
    }

    private fun initChannels(agent: Assistant, config: Map<*, *>) {
        val root = MapUtil.toList("channels", config)
        root?.forEach { node ->
            if (node is Map<*, *>) {
                initChannel(agent, node)
            }
        }
    }

    private fun initChannel(agent: Assistant, config: Map<*, *>) {
        try {
            val type = config["type"]?.toString()
                ?: throw ConfigurationException("channel type is required")

            LOGGER.info("Channel: $type")
            val channel = channelFactory.create(type, agent)
            channel.init(config, this)
            channels.add(channel)
        } catch (ex: Exception) {
            LOGGER.warn("Failed to initialize the channel - ${ex.message}")
        }
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

        chatHistory.init(root, this)
        memory.init(root, this)
    }

    private fun initTools() {
        toolRegistry.init(this)
    }

    private fun initCommands() {
        commandRegistry.init(this)
    }

    private fun initMail(config: Map<*, *>) {
        val root = MapUtil.toMap("mail", config)
            ?: return // No email configuration

        val smtpRoot = root.let { node -> MapUtil.toMap("smtp", node) }
        if (smtpRoot != null) {
            LOGGER.info("Mail: SMTP")
            try {
                smtp.init(smtpRoot, this)
            } catch (ex: Exception) {
                LOGGER.warn("Failed to initialize SMTP - ${ex.message}")
            }
        }

        val imapNode = root.let { node -> MapUtil.toMap("imap", node) }
        if (imapNode != null) {
            LOGGER.info("Mail: IMAP")
            try {
                imap.init(imapNode, this)
            } catch (ex: Exception) {
                LOGGER.warn("Failed to initialize IMAP - ${ex.message}")
            }
        }
    }

    private fun initSkills() {
        skillRegistry.init(this)
    }
}
