package com.wutsi.kokibot

import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.command.HealthCommand
import com.wutsi.kokibot.command.HelpCommand
import com.wutsi.kokibot.command.RenameCommand
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.mcp.McpCommand
import com.wutsi.kokibot.service.credential.CredentialService
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
import com.wutsi.kokibot.tools.mcp.McpActivationTool
import com.wutsi.kokibot.tools.mcp.McpCallTool
import com.wutsi.kokibot.tools.python.PythonTool
import com.wutsi.kokibot.tools.shell.ShellTool
import com.wutsi.kokibot.tools.skill.SkillActivationTool
import com.wutsi.kokibot.tools.swarm.SwarmDelegateTool
import com.wutsi.kokibot.tools.telegram.TelegramSendTool
import com.wutsi.kokibot.tools.user.UserAskQuestionTool
import com.wutsi.kokibot.tools.web.WebFetchTool
import com.wutsi.kokibot.tools.web.WebSearchTool
import com.wutsi.kokibot.util.MapUtil
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File

@Service
class ContextFactory(
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val channelRegistry: ChannelRegistry = ChannelRegistry(ChannelFactory()),
    val llmFactory: LLMFactory = LLMFactory(),
    val commandRegistry: CommandRegistry = CommandRegistry(),
    val skillRegistry: SkillRegistry = SkillRegistry(),
    val jsonMapper: JsonMapper,
    val assistantRegistry: AssistantRegistry,
    val multiBootstrap: MultiBootstrap,
) {
    fun create(home: File, config: Map<*, *>, credentialService: CredentialService): Context {
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
            credentialService = credentialService,
        )
    }

    private fun createLLM(config: Map<*, *>): LLM {
        val root = MapUtil.toMap("llm", config)
        val name = root?.get("name")?.toString() ?: ""

        return llmFactory.create(name)
    }

    private fun discoverTools(): List<Tool> {
        return listOf(
            FileReadTool(),
            FileWriteTool(),
            FileEditTool(),

            PythonTool(),

            ShellTool(),

            McpActivationTool(),
            McpCallTool(),
            SkillActivationTool(),

            SwarmDelegateTool(),

            TelegramSendTool(),

            UserAskQuestionTool(),

            WebSearchTool(),
            WebFetchTool(),
        )
    }

    private fun discoverCommands(): List<Command> {
        return listOf(
            CompactCommand(),
            HealthCommand(),
            HelpCommand(),
            McpCommand(),
            RenameCommand(multiBootstrap),
            SkillCommand(),
            ToolCommand(),
            HeartbeatCommand(),
        )
    }
}
