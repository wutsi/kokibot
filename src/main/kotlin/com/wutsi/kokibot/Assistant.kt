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
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.max

class Assistant(val name: String = "") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val DEFAULT_ITERATIONS = 10
        const val DEFAULT_POOL_SIZE = 4
        const val MIN_POOL_SIZE = 2
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

        val poolSize = max(MIN_POOL_SIZE, MapUtil.toInt("thread-pool-size", config) ?: DEFAULT_POOL_SIZE)
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
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        LOGGER.info(
            "${query.id} $name ${query.userId ?: "-"}@${query.channelId ?: "-"} files=${query.filePaths} " +
                take(query.text, 200)
        )

        val now = System.currentTimeMillis()
        context.sessionLog.onQuery(query.id, 1, query)

        // Process async
        val future = scheduler.submit<Message> {
            doProcessAsync(query, streamCallback)
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
            "${query.id} $name FINAL ANSWER (" + (System.currentTimeMillis() - now) / 1000 + "s): " +
                take(response.text, 200)
        )
        context.chatHistory.append(query, response)
        context.sessionLog.onResponse(query.id, response)
        return response
    }

    private fun doProcessAsync(
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        return try {
            doProcess(query, streamCallback)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(ERROR_TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }
    }

    private fun doProcess(
        query: Message,
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

            val command = getCommand(query)
            if (command != null) {
                val result = exec(iteration, query, command)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                val response = ask(iteration, query, memory, streamCallback)
                if (decide(query.id, iteration, response, memory, tools)) {
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
        query: Message,
        memory: MutableList<String>,
        streamCallback: ((String) -> Unit)?,
    ): LLMResponse {
        LOGGER.info("$iteration $name LLM " + take(query.text, 200))

        // Call LLM
        val request = LLMRequest(
            prompt = buildPrompt(query, memory),
            systemInstructions = listOfNotNull(
                loadIdentify(),
                dailyLogInstructions(),
                chatHistoryInstructions(query),
                skillsInstructions(),
                securityInstructions(),
            ).joinToString("\n\n---\n\n"),
            files = query.filePaths.map { path -> File(path) }
        )

        val tools = context.toolRegistry.all()
        val streamingEnabled = context.llm.supportsStreaming()
        val response = if (streamingEnabled && streamCallback != null) {
            context.llm.completionStream(
                request = request,
                tools = tools,
                onChunk = { chunk ->
                    chunk.reasoningDelta?.let { delta ->
                        streamCallback(delta)
                    }
                }
            )
        } else {
            context.llm.completion(
                request = request,
                tools
            )
        }

        // Record the result
        LOGGER.info("$iteration $name LLM - tokens=" + response.usage?.totalTokens)
        context.sessionLog.onLLMResponse(query.id, iteration, response, memory)

        // Update memory with reasoning content
        response.choices.forEach { choice ->
            if (!choice.content.isNullOrEmpty()) {
                memory.add(choice.content)
            }
        }
        return response
    }

    private fun decide(
        id: String,
        iteration: Int,
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ): Boolean {
        // Tool calls
        val choiceCalls = response.choices.filter { choice -> choice.toolCalls.isNotEmpty() }
        if (choiceCalls.isNotEmpty()) {
            choiceCalls.forEach { choice -> exec(id, iteration, choice, memory, tools) }
            return false
        } else {
            return true
        }
    }

    private fun exec(
        id: String,
        iteration: Int,
        choice: LLMResponseChoice,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        choice.toolCalls.forEach { call ->
            exec(id, iteration, call, memory, tools)
        }
    }

    private fun exec(
        id: String,
        iteration: Int,
        call: LLMToolCall,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
    ) {
        LOGGER.info("$iteration $name TOOL ${call.name} " + take(call.arguments.toString(), 200))
        context.sessionLog.onToolUse(id, iteration, call)

        // Execute
        val tool = tools[call.name]
        val result = if (tool == null) {
            "The tool `${call.name}` is not available!"
        } else {
            try {
                tool.exec(call.arguments)
            } catch (e: Exception) {
                LOGGER.warn("Unexpected error while executing tool `${call.name}`. Error=${e.message}")
                "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
            }
        }

        // Update memory
        memory.add(
            "Using tool `${call.name}` with arguments: " +
                call.arguments.map { entry ->
                    "${entry.key}=" + entry.value?.let { value ->
                        take(value.toString(), 200)
                    }
                }.joinToString(",")
        )
        memory.add(result)

        // Update session log
        context.sessionLog.onToolResult(id, iteration, call, result)
    }

    private fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\r\n", " ").take(n).trim()
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

    private fun buildPrompt(query: Message, memory: List<String>): String {
        val sb = StringBuilder()
        sb.append("Query: ${query.text}")

        // Long-term memory
        val longTermMemory = context.memory.get()
        if (longTermMemory != null) {
            sb.append("\n---\n")
            sb.append("# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$longTermMemory\n```\n")
        }

        // Short-term memory (conversation history)
        val shortTermMemory = context.dailyLog.get()
        if (shortTermMemory != null) {
            sb.append("\n---\n\n")
            sb.append("# Short-Term Memory\n")
            sb.append("Here are information that you have stored in your short-term memory in Markdown format:\n")
            sb.append("```markdown\n$shortTermMemory\n```\n")
        }

        // Reasoning steps and observations
        if (memory.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
            memory.forEach { line -> sb.append("$line\n\n") }
        }

        return sb.toString()
    }

    private fun loadIdentify(): String? {
        val file = File(context.home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText()
                .replace("{{ASSISTANT_NAME}}", name)
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

    private fun securityInstructions(): String {
        return IOUtils.toString(Assistant::class.java.getResource("/instructions/SECURITY.md"), "utf-8")
            .replace("{{HOME}}", context.home.absolutePath)
    }

    private fun dailyLogInstructions(): String {
        return IOUtils.toString(Assistant::class.java.getResourceAsStream("/instructions/DAILY_LOG.md"), "utf-8")
            .replace("{{HOME}}", context.home.absolutePath)
    }

    private fun chatHistoryInstructions(query: Message): String? {
        val userId = query.userId
        val channelId = query.channelId
        if (userId == null || channelId == null) {
            return null
        }
        return IOUtils.toString(Assistant::class.java.getResourceAsStream("/instructions/CHAT_HISTORY.md"), "utf-8")
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}
