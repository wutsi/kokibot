package com.wutsi.kokibot

import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
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
        private const val DEFAULT_ITERATIONS = 10
        const val TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = DEFAULT_ITERATIONS
    private lateinit var context: Context

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
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

    fun process(
        prompt: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        val now = System.currentTimeMillis()
        val response = try {
            doProcess(prompt, streamCallback)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }

        val elapsedTime = (System.currentTimeMillis() - now) / 1000
        LOGGER.info("ANSWER prompt_id=${prompt.id}\n ellapsed_time=${elapsedTime}s: ${response.text}")
        if (response.role != Role.COMMAND) {
            context.chatHistory.append(prompt, response)
        }
        return response
    }

    private fun doProcess(
        prompt: Message,
        streamCallback: ((String) -> Unit)?,
    ): Message {
        var iteration = 0
        val memory = mutableListOf<String>()
        val tools = mutableMapOf<String, Tool>()
        context.toolRegistry.all().map { tool -> tools[tool.metadata().name] = tool }

        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            val command = getCommand(prompt)
            if (command != null) {
                val result = exec(prompt, command)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                val response = ask(iteration, prompt, memory, streamCallback)
                if (decide(response, memory, tools)) {
                    return Message(
                        text = response.choices.mapNotNull { choice -> choice.content }.joinToString("\n\n"),
                        role = Role.ASSISTANT,
                        finishReason = FinishReason.DONE,
                    )
                }
            }
        }
    }

    private fun ask(
        iteration: Int,
        prompt: Message,
        memory: MutableList<String>,
        streamCallback: ((String) -> Unit)?,
    ): LLMResponse {
        LOGGER.debug("\n\n--- Iteration $iteration -------------------")
        LOGGER.info("$iteration - PROMPT: prompt_id=${prompt.id}  user=${prompt.userId}@${prompt.channelId}\n${prompt.text}")

        val tools = context.toolRegistry.all()
        val promptText = buildPrompt(prompt, memory)
        val systemInstructions = buildSystemInstructions()

        val streamingEnabled = context.llm.supportsStreaming()

        return if (streamingEnabled && streamCallback != null) {
            LOGGER.info("Using streaming mode")
            context.llm.completionStream(
                request = LLMRequest(promptText, systemInstructions),
                tools = tools,
                onChunk = { chunk ->
                    chunk.reasoningDelta?.let { delta ->
                        streamCallback(delta)
                    }
                }
            )
        } else {
            LOGGER.info("Using non-streaming mode")
            context.llm.completion(
                request = LLMRequest(promptText, systemInstructions),
                tools
            )
        }
    }

    private fun decide(
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ): Boolean {
        // Tool calls
        val choiceCalls = response.choices.filter { choice -> choice.toolCalls.isNotEmpty() }
        if (choiceCalls.isNotEmpty()) {
            choiceCalls.forEach { choice -> exec(choice, memory, tools) }
            return false
        } else {
            return true
        }
    }

    private fun exec(
        choice: LLMResponseChoice,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        LOGGER.info(">>> ${choice.toolCalls.size} call(s) to execute")
        choice.toolCalls.forEach { call ->
            exec(choice.content, call, memory, tools)
        }
    }

    private fun exec(
        content: String?,
        call: LLMToolCall,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        content?.let { LOGGER.info(">>> $content") }
        LOGGER.info(">>> Tool execution: name=${call.name}, arguments=${call.arguments}")

        val tool = tools[call.name]
        if (tool == null) {
            memory.add("Calling the tool `${call.name}` failed because it's not available!")
            return
        }

        val result = try {
            tool.exec(call.arguments)
        } catch (e: Exception) {
            LOGGER.warn("Error while executing tool `${call.name}` with arguments ${call.arguments}", e)
            "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
        }
        if (result.length > 200) {
            LOGGER.info(result.take(100) + "...")
        } else {
            LOGGER.info(result)
        }

        if (content != null) {
            memory.add(content)
        }
        memory.add("Calling the tool `${call.name}` returned the following result:\n$result")
    }

    private fun getCommand(query: Message): Command? {
        val text = query.text.trim()
        if (!text.startsWith("/")) {
            return null
        }

        val name = text.split(" ").firstOrNull() ?: return null
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

        LOGGER.info("Command execution: name={} - input={}", name, input)
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
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "- `${skill.metadata.name}`: ${skill.metadata.description}",
                ).joinToString("\n")
            }
            .ifEmpty { null }

        return skills?.let { "\n# Available skills\n$skills" }
    }

    private fun buildSecurityInstructions(): String {
        return this::class.java.getResourceAsStream("/SECURITY.md")!!
            .bufferedReader()
            .readText()
            .replace("{{HOME}}", context.home.absolutePath)
    }
}
