package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.date.ClockTool
import com.wutsi.kokibot.tools.mail.MailFindTool
import com.wutsi.kokibot.tools.mail.MailListTool
import com.wutsi.kokibot.tools.mail.MailReadTool
import com.wutsi.kokibot.tools.mail.MailSendTool
import com.wutsi.kokibot.tools.mail.MailUnsubscribeTool
import com.wutsi.kokibot.tools.python.PythonTool
import com.wutsi.kokibot.tools.shell.ShellTool
import com.wutsi.kokibot.tools.web.WebFetchTool
import com.wutsi.kokibot.tools.web.WebSearchTool
import com.wutsi.kokibot.util.MapUtil
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class Bootstrap(
    val channelFactory: ChannelFactory = ChannelFactory(),
    val llmFactory: LLMFactory = LLMFactory(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val jsonMapper: JsonMapper
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Bootstrap::class.java)
    }

    lateinit var assistant: Assistant
    internal val channels = mutableListOf<Channel>()

    @PostConstruct
    fun init() {
        val home = System.getProperty("user.home") + "/kokibot"
        init(File(home))
    }

    @PreDestroy
    fun destroy() {
        channels.forEach { channel -> channel.destroy() }
        toolRegistry.tools.values.forEach { tool -> tool.destroy() }
        assistant.destroy()
    }

    internal fun init(home: File) {
        LOGGER.info("Initializing form $home")

        val config = loadConfig(File(getConfigDir(home), "settings.json"))
        val context = Context(
            home = home,
            llm = setupLLM(config),
            toolRegistry = toolRegistry,
            chatHistory = ChatHistory(home, jsonMapper),
            config = config,
        )

        assistant = setupAssistant(config, context)
        setupChannels(assistant, config)
        setupTools(home, context)
    }

    private fun setupAssistant(config: Map<*, *>, context: Context): Assistant {
        val assistant = Assistant()
        val root = MapUtil.toMap("assistant", config) ?: emptyMap<String, Any>()
        assistant.init(root, context)
        return assistant
    }

    private fun setupChannels(agent: Assistant, config: Map<*, *>) {
        val root = MapUtil.toList("channels", config)
        root?.forEach { node ->
            if (node is Map<*, *>) {
                setupChannel(agent, node)
            }
        }
    }

    private fun setupChannel(agent: Assistant, config: Map<*, *>) {
        val type = config["type"]?.toString()
            ?: throw ConfigurationException("channel type is required")

        LOGGER.info("Channel: $type")
        val channel = channelFactory.create(type, agent)
        channel.init(config)
        channels.add(channel)
    }

    private fun setupLLM(config: Map<*, *>): LLM {
        val root = MapUtil.toMap("llm", config)
            ?: throw ConfigurationException("LLM has invalid structure or missing")

        val type = root["type"]?.toString()
            ?: throw ConfigurationException("LLM type is required")

        LOGGER.info("LLM: $type")
        val llm = llmFactory.create(type)
        llm.init(root, toolRegistry)
        return llm
    }

    private fun setupTools(home: File, context: Context) {
        val tools = discoverTools()
        tools.forEach { tool ->
            LOGGER.info("Tool: ${tool.metadata().name}")
            setupTool(home, tool, context)
            toolRegistry.register(tool)
        }
    }

    private fun setupTool(home: File, tool: Tool, context: Context) {
        val dir = File(getConfigDir(home), "tools")
        val file = File(dir, tool.metadata().name + ".json")
        if (file.exists()) {
            val config = loadConfig(file)
            tool.init(config, context)
        } else {
            tool.init(emptyMap<String, Any>(), context)
        }
    }

    private fun discoverTools(): List<Tool> {
        return listOf(
            /* date */
            ClockTool(),

            /* Mail */
            MailListTool(),
            MailReadTool(),
            MailSendTool(),
            MailFindTool(),
            MailUnsubscribeTool(),

            /* Python */
            PythonTool(),

            // Shell
            ShellTool(),

            /* Web */
            WebSearchTool(),
            WebFetchTool(),
        )
    }

    private fun loadConfig(file: File): Map<*, *> {
        val config = JsonMapper().readValue(file, Map::class.java)
        return MapUtil.applyEnv(config)
    }

    private fun getConfigDir(home: File): File {
        return File(home, "config")
    }
}
