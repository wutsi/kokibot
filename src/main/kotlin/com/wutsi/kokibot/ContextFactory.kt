package com.wutsi.kokibot

import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.command.HealthCommand
import com.wutsi.kokibot.command.HelpCommand
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.ClearCommand
import com.wutsi.kokibot.service.memory.CompactCommand
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.SkillCommand
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolCommand
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.file.FileRead
import com.wutsi.kokibot.tools.file.FileWrite
import com.wutsi.kokibot.tools.python.PythonTool
import com.wutsi.kokibot.tools.shell.ShellTool
import com.wutsi.kokibot.tools.skill.SkillActivationTool
import com.wutsi.kokibot.tools.web.WebFetchTool
import com.wutsi.kokibot.tools.web.WebSearchTool
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ContextFactory(
    private val toolRegistry: ToolRegistry,
    private val channelRegistry: ChannelRegistry,
    private val llmFactory: LLMFactory,
    private val commandRegistry: CommandRegistry,
    private val skillRegistry: SkillRegistry,
    private val jsonMapper: JsonMapper,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContextFactory::class.java)
    }

    fun create(home: File, config: Map<*, *>): Context {
        // Tools
        discoverTools().forEach { tool -> toolRegistry.register(tool) }

        // Commands
        discoverCommands().forEach { command -> commandRegistry.register(command) }

        // Context
        return Context(
            home = home,
            llm = createLLM(config),
            toolRegistry = toolRegistry,
            channelRegistry = channelRegistry,
            commandRegistry = commandRegistry,
            skillRegistry = skillRegistry,
            chatHistory = ChatHistory(),
            memory = Memory(),
            config = config,
            jsonMapper = jsonMapper,
        )
    }

    private fun createLLM(config: Map<*, *>): LLM {
        val root = MapUtil.toMap("llm", config)
        val type = root?.get("type")?.toString() ?: ""

        LOGGER.info("LLM: $type")
        return llmFactory.create(type)
    }

    private fun discoverTools(): List<Tool> {
        return listOf(
            FileRead(),
            FileWrite(),

            PythonTool(),

            ShellTool(),

            SkillActivationTool(),

            WebSearchTool(),
            WebFetchTool(),
        )
    }

    private fun discoverCommands(): List<Command> {
        return listOf(
            ClearCommand(),
            CompactCommand(),
            HealthCommand(),
            HelpCommand(),
            SkillCommand(),
            ToolCommand(),
        )
    }
}
