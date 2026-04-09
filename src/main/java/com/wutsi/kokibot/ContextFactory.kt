package com.wutsi.kokibot

import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.memory.Memory
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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ContextFactory(
    private val channelFactory: ChannelFactory,
    private val toolRegistry: ToolRegistry,
    private val llmFactory: LLMFactory,
    private val commandRegistry: CommandRegistry,
    private val jsonMapper: JsonMapper,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContextFactory::class.java)
    }

    fun create(home: File, config: Map<*, *>): Context {
        // Tools
        discoverTools().forEach { tool -> toolRegistry.register(tool) }

        // Context
        return Context(
            home = home,
            llm = createLLM(config),
            toolRegistry = toolRegistry,
            channelFactory = channelFactory,
            chatHistory = ChatHistory(),
            commandRegistry = commandRegistry,
            memory = Memory(),
            config = config,
            jsonMapper = jsonMapper,
        )
    }

    private fun createLLM(config: Map<*, *>): LLM {
        val root = MapUtil.toMap("llm", config)
        val type = root?.get("type")?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing configuration: llm/type")

        LOGGER.info("LLM: $type")
        return llmFactory.create(type)
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
}
