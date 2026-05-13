package com.wutsi.kokibot

import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.command.HealthCommand
import com.wutsi.kokibot.command.HelpCommand
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.service.heartbeat.HeartbeatCommand
import com.wutsi.kokibot.service.memory.CompactCommand
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.SkillCommand
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolCommand
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.tools.file.FileEditTool
import com.wutsi.kokibot.tools.file.FileReadTool
import com.wutsi.kokibot.tools.file.FileWriteTool
import com.wutsi.kokibot.tools.messaging.SendMessageTool
import com.wutsi.kokibot.tools.python.PythonTool
import com.wutsi.kokibot.tools.shell.ShellTool
import com.wutsi.kokibot.tools.skill.SkillActivationTool
import com.wutsi.kokibot.tools.swarm.SwarmDelegateTool
import com.wutsi.kokibot.tools.web.WebFetchTool
import com.wutsi.kokibot.tools.web.WebSearchTool
import com.wutsi.kokibot.util.MapUtil
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ContextFactory(
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val channelRegistry: ChannelRegistry = ChannelRegistry(ChannelFactory()),
    private val llmFactory: LLMFactory = LLMFactory(),
    private val commandRegistry: CommandRegistry = CommandRegistry(),
    private val skillRegistry: SkillRegistry = SkillRegistry(),
    private val jsonMapper: JsonMapper,
    val assistantRegistry: AssistantRegistry,
) {
    fun create(home: File, config: Map<*, *>): Context {
        // Tools
        discoverTools().forEach { tool -> toolRegistry.register(tool) }

        // Commands
        discoverCommands().forEach { command -> commandRegistry.register(command) }

        // Context
        return Context(
            home = home,
            llm = createLLM(config),
            assistant = Assistant(home.name),
            toolRegistry = toolRegistry,
            channelRegistry = channelRegistry,
            commandRegistry = commandRegistry,
            skillRegistry = skillRegistry,
            dailyLog = DailyLog(),
            memory = Memory(),
            config = config,
            jsonMapper = jsonMapper,
            assistantRegistry = assistantRegistry,
        )
    }

    private fun createLLM(config: Map<*, *>): LLM {
        val root = MapUtil.toMap("llm", config)
        val type = root?.get("type")?.toString() ?: ""

        return llmFactory.create(type)
    }

    private fun discoverTools(): List<Tool> {
        return listOf(
            FileReadTool(),
            FileWriteTool(),
            FileEditTool(),

            PythonTool(),

            SendMessageTool(),

            ShellTool(),

            SkillActivationTool(),

            SwarmDelegateTool(),

            WebSearchTool(),
            WebFetchTool(),
        )
    }

    private fun discoverCommands(): List<Command> {
        return listOf(
            CompactCommand(),
            HealthCommand(),
            HelpCommand(),
            SkillCommand(),
            ToolCommand(),
            HeartbeatCommand(),
        )
    }
}
