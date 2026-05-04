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

        LOGGER.info(
            ".......................................\n" +
                " FINAL ANSWER\n" +
                " Duration: ${(System.currentTimeMillis() - now) / 1000}s\n" +
                " Result: ${clip(response.text, 200)}"
        )
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

        LOGGER.debug("\n\n------------------------------------------------------------")
        LOGGER.info("$iteration - prompt_id=${prompt.id}  user=${prompt.userId}@${prompt.channelId}\n${prompt.text}")

        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            val command = getCommand(prompt)
            if (command != null) {
                val result = exec(iteration, prompt, command)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                val response = ask(iteration, prompt, memory, streamCallback)
                if (decide(iteration, response, memory, tools)) {
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
        val tools = context.toolRegistry.all()
        val promptText = buildPrompt(prompt, memory)
        val systemInstructions = buildSystemInstructions()
        val streamingEnabled = context.llm.supportsStreaming()

        LOGGER.info(
            ".......................................\n" +
                "$iteration - Asking LLM with the following prompt:\n" +
                " Files: ${prompt.filePaths}\n" +
                " Query: ${clip(prompt.text, 200)}\n" +
                " Memory: ${memory.size} items\n" +
                " Tools: ${tools.size} available"
        )

        return if (streamingEnabled && streamCallback != null) {
            context.llm.completionStream(
                request = LLMRequest(
                    promptText,
                    systemInstructions,
                    files = prompt.filePaths.map { path -> File(path) }
                ),
                tools = tools,
                onChunk = { chunk ->
                    chunk.reasoningDelta?.let { delta ->
                        streamCallback(delta)
                    }
                }
            )
        } else {
            context.llm.completion(
                request = LLMRequest(
                    promptText,
                    systemInstructions,
                    files = prompt.filePaths.map { path -> File(path) }
                ),
                tools
            )
        }
    }

    private fun decide(
        iteration: Int,
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ): Boolean {
        // Tool calls
        val choiceCalls = response.choices.filter { choice -> choice.toolCalls.isNotEmpty() }
        if (choiceCalls.isNotEmpty()) {
            choiceCalls.forEach { choice -> exec(iteration, choice, memory, tools) }
            return false
        } else {
            return true
        }
    }

    private fun exec(
        iteration: Int,
        choice: LLMResponseChoice,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        choice.toolCalls.forEach { call ->
            exec(iteration, choice.content, call, memory, tools)
        }
    }

    private fun exec(
        iteration: Int,
        content: String?,
        call: LLMToolCall,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        LOGGER.info(
            ".......................................\n" +
                (content?.let { clip(content, 200) + "\n" } ?: "") +
                "$iteration - TOOL: ${call.name}\n" +
                " Arguments:\n" +
                call.arguments.map { " - " + clip(it.toString(), 200) }.joinToString("\n")
        )

        val tool = tools[call.name]
        if (tool == null) {
            memory.add("The tool `${call.name}` is not available!")
            return
        }

        val result = try {
            tool.exec(call.arguments)
        } catch (e: Exception) {
            LOGGER.warn("Unexpected error while executing tool `${call.name}`. Error=${e.message}")
            "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
        }

//        if (content != null) {
//            memory.add(content)
//        }
        memory.add(result)
    }

    private fun clip(text: String, n: Int): String {
        return if (text.length > n) {
            text.take(n) + "..."
        } else {
            text
        }
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

    private fun exec(iteration: Int, query: Message, command: Command): String {
        val text = query.text.trim()
        val name = command.metadata().name
        val input = if (text.equals(name, ignoreCase = true)) {
            ""
        } else {
            text.substring(name.length).trim()
        }

        LOGGER.info("$iteration - COMMAND: {} {}", name, input)
        return command.exec(input, context)
    }

    private fun buildPrompt(prompt: Message, memory: List<String>): String {
        val sb = StringBuilder()
        sb.append("Query: ${prompt.text}")

        // Long-term memory
        val longTermMemory = context.memory.get()
        if (longTermMemory != null) {
            sb.append("\n\n---\n\n")
            sb.append("# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$longTermMemory\n```\n")
        }

        // Short-term memory (conversation history)
        val shortTermMemory = context.chatHistory.get()
        if (shortTermMemory != null) {
            sb.append("\n\n---\n\n")
            sb.append("# Conversation history\n")
            sb.append("Here is the conversation history between you and the user in JSON format:\n")
            sb.append("```json\n$shortTermMemory\n```\n")
        }

        // Reasoning steps and observations
        if (memory.isNotEmpty()) {
            sb.append("\n\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
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
            null
        }
    }

    private fun describeSkills(): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "- Skill: `${skill.metadata.name}`\n" +
                        "    - Description: ${skill.metadata.description}\n" +
                        "    - Home Directory: ${skill.metadata.home}"
                ).joinToString("\n")
            }
            .ifEmpty { null }

        return skills?.let { "\n\n---\n\n# Available skills\n$skills" }
    }

    private fun buildSecurityInstructions(): String? {
        val file = File(context.home, "SECURITY.md")
        return if (file.exists()) {
            return file.readText()
                .replace("{{HOME}}", context.home.absolutePath)
        } else {
            null
        }
    }
}
