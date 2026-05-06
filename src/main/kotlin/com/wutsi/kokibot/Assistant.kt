package com.wutsi.kokibot

import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Assistant(val name: String = "") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val DEFAULT_ITERATIONS = 10
        const val DEFAULT_POOL_SIZE = 1
        const val DEFAULT_MAX_DURATION_MINUTES = 5L
        const val ERROR_TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val ERROR_TIMEOUT = "Oups, the request has been cancelled because it took too much time to process."
        const val ERROR_FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = DEFAULT_ITERATIONS
    private var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
    private lateinit var description: String
    private lateinit var context: Context
    private lateinit var scheduler: ScheduledExecutorService

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
        description = MapUtil.toString("description", config) ?: ""
        maxDurationMinutes = MapUtil.toString("max-duraction", config)
            ?.let { value -> DurationUtil.minutes(value, DEFAULT_MAX_DURATION_MINUTES) }
            ?: DEFAULT_MAX_DURATION_MINUTES

        val poolSize = MapUtil.toInt("thread-pool-size", config) ?: DEFAULT_POOL_SIZE
        scheduler = Executors.newScheduledThreadPool(poolSize)

        this.context = context
    }

    fun destroy() {
        try {
            scheduler.shutdown()
        } catch (e: Exception) {
            LOGGER.warn("Error while shutting down scheduler", e)
        }
    }

    fun process(
        prompt: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        LOGGER.info(
            "prompt_id=${prompt.id} role=${prompt.role} user=${prompt.userId} channel=${prompt.channelId ?: "-"} files=${prompt.filePaths}\n" +
                (prompt.subject?.let { subject -> "Subject: $subject\n" } ?: "") +
                prompt.text
        )
        val now = System.currentTimeMillis()

        // Process async
        val future = scheduler.submit<Message> {
            doProcessAsync(prompt, streamCallback)
        }

        // What for the response with timeout
        val response = try {
            future.get(maxDurationMinutes, TimeUnit.MINUTES)
        } catch (_: TimeoutException) {
            future.cancel(true)
            Message(ERROR_TIMEOUT, Role.ASSISTANT, FinishReason.TIMEOUT)
        } catch (e: Exception) {
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }

        // Result
        LOGGER.info(
            "... @$name ....................................\n" +
                " FINAL ANSWER\n" +
                " Duration: ${(System.currentTimeMillis() - now) / 1000}s\n" +
                " Result: ${response.text}"
        )
        return response
    }

    private fun doProcessAsync(
        prompt: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        return try {
            doProcess(prompt, streamCallback)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(ERROR_TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }
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
        val systemInstructions = listOfNotNull(
            loadIdentify(),
            dailyLogInstructions(),
            skillsInstructions(),
            securityInstructions(),
        ).joinToString("\n\n---\n\n")

        val streamingEnabled = context.llm.supportsStreaming()

        LOGGER.info(
            "... $iteration @$name ....................................\n" +
                "Asking LLM with the following prompt:\n" +
                "  Files: ${prompt.filePaths}\n" +
                "  Query: ${clip(prompt.text, 200, 1)}\n" +
                "  Memory: ${memory.size} items\n" +
                "  Tools: ${tools.size} available"
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
        val xcontent = content?.trim()
            ?.ifEmpty { null }
            ?.let { clip(content, 200, 1) + "\n" } ?: ""
        LOGGER.info(
            "... $iteration @$name ....................................\n" +
                "${xcontent}TOOL: ${call.name}\n" +
                "  Arguments:\n" +
                call.arguments
                    .map { "  - " + clip(it.toString(), 200, 1) }
                    .joinToString("\n")
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
        LOGGER.info(clip(result, 200, 4))
        memory.add(result)
    }

    private fun clip(text: String, n: Int, l: Int): String {
        val xtext = text
            .lines()
            .filter { line -> line.isNotBlank() }
            .take(l)
            .joinToString("\n")
            .take(n)
            .trim()
        return if (text.length > n) {
            "$xtext..."
        } else {
            xtext
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
        val shortTermMemory = context.dailyLog.get()
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

    private fun loadIdentify(): String? {
        val file = File(context.home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText()
                .replace("{{assistant_name}}", name)
        } else {
            null
        }
    }

    private fun skillsInstructions(): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "## Skill: ${skill.metadata.name}\n\n" +
                        "**Home Directory:** ${skill.metadata.home}\n\n" +
                        "**Description:** ${skill.metadata.description}"
                ).joinToString("\n\n")
            }
            .ifEmpty { null }

        return skills?.let { "# Available skills\n\nHere are the skills available:\n\n$skills" }
    }

    private fun securityInstructions(): String? {
        return File(this::class.java.getResource("/instructions/SECURITY.md")!!.toURI()).readText()
            .replace("{{HOME}}", context.home.absolutePath)
    }

    private fun dailyLogInstructions(): String {
        return File(this::class.java.getResource("/instructions/DAILY_LOG.md")!!.toURI()).readText()
            .replace("{{HOME}}", context.home.absolutePath)
    }
}
