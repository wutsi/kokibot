package com.wutsi.kokibot

import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.exception.CommandNotFoundException
import com.wutsi.kokibot.exception.TooManyIterationException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File

class Assistant {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val MAX_ITERATIONS = 10
        const val TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = MAX_ITERATIONS
    private var accessWorkspaceOnly: Boolean = true
    private lateinit var context: Context

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: MAX_ITERATIONS
        accessWorkspaceOnly = MapUtil.toBoolean("access-workspace-only", config) ?: true
        this.context = context

        // Create temporary directory if it does not exist
        val tmp = File("${context.home.absolutePath}/workspace/tmp")
        if (!tmp.exists()) {
            LOGGER.info("Creating temporary directory: $tmp")
            tmp.mkdirs()
        }
    }

    fun destroy() {
    }

    fun process(prompt: Message): Message {
        val response = try {
            doProcess(prompt)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }

        LOGGER.debug("answer: ${response.text}")
        if (response.role != Role.COMMAND) {
            context.chatHistory.append(prompt, response)
        }
        return response
    }

    private fun doProcess(prompt: Message): Message {
        var iteration = 0
        val memory = mutableListOf<String>()
        val tools = mutableMapOf<String, Tool>()
        context.toolRegistry.all().map { tool -> tools[tool.metadata().name] = tool }

        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            LOGGER.debug("-- ITERATION: $iteration --------------------------------------------------------------")
            val command = getCommand(prompt)
            if (command != null) {
                val result = exec(prompt, command)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                val response = ask(prompt, memory, tools)
                if (decide(prompt, response, memory, tools)) {
                    return Message(
                        text = response.choices.first().content,
                        role = Role.ASSISTANT,
                        finishReason = FinishReason.DONE,
                    )
                }
            }
        }
    }

    private fun ask(query: Message, memory: MutableList<String>, tools: MutableMap<String, Tool>): LLMResponse {
        LOGGER.debug("LLM chat: ${query.text}")

        val tools = context.toolRegistry.all()
        val prompt = buildPrompt(query, memory)
        val systemInstructions = buildSystemInstructions()
        return context.llm.completion(
            request = LLMRequest(prompt, systemInstructions),
            tools,
        )
    }

    private fun decide(
        query: Message,
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>
    ): Boolean {
        // Tool calls
        val choiceCalls = response.choices.filter { choice -> choice.toolCalls.isNotEmpty() }
        if (choiceCalls.isNotEmpty()) {
            LOGGER.debug("FUNCTION CALLS")
            choiceCalls.forEach { choice -> exec(choice, memory, tools, query) }
            return false
        } else {
            return true
        }
    }

    private fun exec(choice: LLMResponseChoice, memory: MutableList<String>, tools: Map<String, Tool>, query: Message) {
        LOGGER.debug(choice.content)

        choice.toolCalls.forEach { call ->
            exec(choice.content, call, memory, tools, query)
        }
    }

    private fun exec(
        content: String,
        call: LLMToolCall,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message
    ) {
        LOGGER.debug("Tool execution: name={} - arguments={}", call.name, call.arguments)
        reply(content, query)
        val tool = tools[call.name]
        if (tool == null) {
            memory.add("Tool `${call.name}` is not available!")
            return
        }

        val result = tool.exec(call.arguments)

        memory.add(content)
        memory.add("Calling the tool `${call.name}` returned the following result: $result")
    }

    private fun reply(content: String, query: Message) {
        if (content.isEmpty()) {
            return
        }

        try {
            val channelId = query.channelId ?: return
            val userId = query.userId ?: return
            context.channelRegistry.get(channelId).send(
                Message(
                    userId = userId,
                    channelId = channelId,
                    text = "$content...",
                )
            )
        } catch (ex: Exception) {
            LOGGER.warn("Unable to send message to user ${query.userId} in channel ${query.channelId}", ex)
        }
    }

    private fun getCommand(query: Message): Command? {
        val text = query.text.trim()
        if (!text.startsWith("/")) {
            return null
        }

        val name = text.split(" ")[0]
        try {
            return context.commandRegistry.get(name)
        } catch (ex: CommandNotFoundException) {
            LOGGER.warn("Command not found: $name", ex)
            return object : Command {
                override fun metadata(): CommandMetadata {
                    return CommandMetadata(name = "")
                }

                override fun exec(input: String, context: Context): String {
                    return "Invalid command: $name.\nUse /help to get the list of available commands."
                }
            }
        }
    }

    private fun exec(query: Message, command: Command): String {
        val text = query.text.trim()
        val name = command.metadata().name
        val input = if (text.equals(name, ignoreCase = true)) {
            ""
        } else {
            text.substring(name.length).trim()
        }

        LOGGER.debug("Command execution: name={} - input={}", name, input)
        return command.exec(input, context)
    }

    private fun buildPrompt(prompt: Message, memory: List<String>): String {
        val sb = StringBuilder()
        sb.append("Query: ${prompt.text}")

        val longTermMemory = context.memory.get()
        if (longTermMemory != null) {
            sb.append("\n\n# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$longTermMemory\n```\n")
        }

        val shortTermMemory = context.chatHistory.get()
        if (shortTermMemory != null) {
            sb.append("\n\n# Conversation history\n")
            sb.append("Here is the conversation history between you and the user in JSON format:\n")
            sb.append("```json\n$shortTermMemory\n```\n")
        }

        if (memory.isNotEmpty()) {
            sb.append("\n\n# Previous reasoning steps and observations")
            memory.forEach { line -> sb.append("$line\n\n") }
        }
        return sb.toString()
    }

    private fun buildSystemInstructions(): String? {
        val identity = loadIdentify()
        val skills = describeSkills()
        val security = buildSecurityInstructions()

        return listOfNotNull(identity, skills, security)
            .joinToString("\n\n")
            .ifEmpty { null }
    }

    private fun loadIdentify(): String? {
        val file = File(context.home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    private fun describeSkills(): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .map { skill ->
                listOfNotNull(
                    "- `${skill.metadata.name}`: ${skill.metadata.description}",
                ).joinToString("\n")
            }
            .ifEmpty { null }

        return skills?.let { "\n# Available skills\n$skills" }
    }

    private fun buildSecurityInstructions(): String {
        val workspace = if (accessWorkspaceOnly) {
            """
                For security reasons, you can only access files and execute commands within the workspace directory.
                The workspace directory is located at `${context.home}/workspace`.
                NEVER attempt to use ../ to escape this directory
            """.trimIndent()
        } else {
            """
                You have access to the entire file system and can execute any command.
            """.trimIndent()
        }

        return """
            # Security instructions
            $workspace

            Use `${context.home}/workspace/tmp/<UUID>` as a temporary directory if you need to create temporary files,
            where <UUID> is a unique identifier generated using the `uuidgen` command.
            Example: `${context.home}/workspace/tmp/70030408-7347-4853-B672-23C3D242E4FB/foo.pdf`
            """.trimIndent()
    }
}
