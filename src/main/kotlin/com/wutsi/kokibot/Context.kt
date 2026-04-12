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
        llm.destroy()
        chatHistory.destroy()
        memory.destroy()
        toolRegistry.destroy()
        commandRegistry.destroy()
        skillRegistry.destroy()
        smtp.destroy()
        imap.destroy()
        channels.forEach { it.destroy() }
        channels.clear()
    }

    fun init(assistant: Assistant, config: Map<*, *>) {
        initChannels(assistant, config)
        initLLM(config)
        initMemory(config)
        initTools()
        initCommands()
        initMail(config)
        initSkills()
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
        val type = config["type"]?.toString()
            ?: throw ConfigurationException("channel type is required")

        LOGGER.info("Channel: $type")
        val channel = channelFactory.create(type, agent)
        channel.init(config, this)
        channels.add(channel)
    }

    private fun initLLM(config: Map<*, *>) {
        val root = MapUtil.toMap("llm", config)
            ?: throw ConfigurationException("LLM has invalid structure or missing")

        llm.init(root, this)
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

        val smtpRoot = root?.let { node -> MapUtil.toMap("smtp", node) }
        if (smtpRoot != null) {
            LOGGER.info("Mail: SMTP")
            smtp.init(smtpRoot, this)
        }

        val imapNode = root?.let { node -> MapUtil.toMap("imap", node) }
        if (imapNode != null) {
            LOGGER.info("Mail: IMAP")
            imap.init(imapNode, this)
        }
    }

    private fun initSkills() {
        skillRegistry.init(this)
    }
}
