package com.wutsi.kokibot

import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.SessionContext
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Assistant(val name: String = "") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val DEFAULT_ITERATIONS = 10
        const val DEFAULT_MAX_DURATION_MINUTES = 5L
        const val ERROR_TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val ERROR_TIMEOUT = "Oups, the request has been cancelled because it took too much time to process."
        const val ERROR_FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = DEFAULT_ITERATIONS
    private var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
    lateinit var description: String
    private lateinit var context: Context
    private var coordinator: Boolean = false
    private var threadPoolSize: Int = 4
    private lateinit var toolExecutor: ExecutorService

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
        description = MapUtil.toString("description", config) ?: ""
        coordinator = MapUtil.toBoolean("coordinator", config) ?: false
        maxDurationMinutes = MapUtil.toString("max-duration", config)
            ?.let { value -> DurationUtil.minutes(value, DEFAULT_MAX_DURATION_MINUTES) }
            ?: DEFAULT_MAX_DURATION_MINUTES

        // Initialize thread pool size
        threadPoolSize = MapUtil.toInt("thread-pool-size", config) ?: 4
        if (threadPoolSize < 2) {
            LOGGER.warn("thread-pool-size must be at least 2, using 2")
            threadPoolSize = 2
        }
        toolExecutor = Executors.newFixedThreadPool(threadPoolSize)

        this.context = context
        context.assistantRegistry.register(this)

        LOGGER.info("Assistant: $name")
        LOGGER.info("  coordinator: $coordinator")
        LOGGER.info("  max-duration: ${maxDurationMinutes}m")
        LOGGER.info("  max-iterations: $maxIterations")
        LOGGER.info("  thread-pool-size: $threadPoolSize")
    }

    fun destroy() {
        if (::toolExecutor.isInitialized) {
            LOGGER.info("Shutting down tool executor for assistant: $name")
            toolExecutor.shutdown()
            try {
                if (!toolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOGGER.warn("Tool executor did not terminate in 30s, forcing shutdown")
                    toolExecutor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                LOGGER.warn("Interrupted while waiting for tool executor shutdown")
                toolExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    fun contextLength(): Int {
        return buildPrompt(Message(), emptyList()).length
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

        // Push to delegation stack
        try {
            context.delegationStack.push(query.id, name, streamCallback)
        } catch (e: Exception) {
            LOGGER.error("Delegation stack push failed for $name", e)
            return Message("Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }

        // Process async
        val timer = Executors.newSingleThreadExecutor()
        val future = timer.submit<Message> {
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
        } finally {
            try {
                timer.shutdown()
            } catch (e: Exception) {
                LOGGER.warn("Error while shutting down scheduler. ${e.message}")
            }
            // Pop from delegation stack - RAII principle
            context.delegationStack.pop(query.id)
        }

        // Result
        val duration = DurationUtil.hms(System.currentTimeMillis() - now)
        LOGGER.info(
            "${query.id} $name FINAL ANSWER ($duration): " +
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
                if (decide(query.id, iteration, response, memory, tools, query)) {
                    return Message(
                        text = response.choices.mapNotNull { choice -> choice.content }.joinToString("\n\n"),
                        role = Role.ASSISTANT,
                        finishReason = FinishReason.DONE,
                    )
                } else {
                    if (streamCallback != null) {
                        response.choices.forEach { choice ->
                            if (!choice.content.isNullOrEmpty()) {
                                streamCallback(choice.content)
                            }
                        }
                    }
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
                if (coordinator) coordinatorInstructions() else null,
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
        LOGGER.info("$iteration $name LLM - tokens=" + response.usage?.totalTokens + ", cached=" + response.usage?.promptCacheHitTokens)
        context.sessionLog.onLLMResponse(query.id, iteration, response, memory)

        // Update memory with reasoning content
        response.choices.forEach { choice ->
            if (!choice.content.isNullOrEmpty()) {
                LOGGER.info(take(choice.content, 200))
                memory.add(choice.content)
            }
        }
        return response
    }

    private fun createToolCallable(
        id: String,
        iteration: Int,
        call: LLMToolCall,
        tools: Map<String, Tool>,
    ): Callable<ToolExecutionResult> {
        return Callable {
            SessionContext.set(id, name)
            try {
                val startTime = System.currentTimeMillis()
                LOGGER.info(
                    "$iteration $name TOOL ${call.name} " +
                        call.arguments.map { entry ->
                            "${entry.key}=" + entry.value?.let { value -> take(value.toString(), 200) }
                        }.joinToString(",")
                )
                context.sessionLog.onToolUse(id, iteration, call)

                val result = tools[call.name]?.let { tool ->
                    try {
                        tool.exec(call.arguments)
                    } catch (e: Exception) {
                        val duration = System.currentTimeMillis() - startTime
                        LOGGER.warn("Unexpected error while executing tool `${call.name}` after ${duration}ms. Error=${e.message}")
                        "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                    }
                }
                ToolExecutionResult(call = call, result = result ?: "Tool `${call.name}` not found")
            } finally {
                SessionContext.clear()
            }
        }
    }

    private fun decide(
        id: String,
        iteration: Int,
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
    ): Boolean {
        // Collect all tool calls from all choices
        val allToolCalls = response.choices
            .flatMap { choice -> choice.toolCalls }

        if (allToolCalls.isEmpty()) {
            return true // No tool calls, done
        }

        // Execute all tool calls in parallel
        execParallel(id, iteration, allToolCalls, memory, tools, query)
        return false
    }

    private fun execParallel(
        id: String,
        iteration: Int,
        toolCalls: List<LLMToolCall>,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
    ) {
        if (toolCalls.isEmpty()) {
            return
        }

        LOGGER.info("$iteration $name Executing ${toolCalls.size} tool calls in parallel")

        // Send status before tool execution
        sendToolStatus(query, toolCalls)

        // Create callables for each tool call
        val callables = toolCalls.map { call ->
            createToolCallable(id, iteration, call, tools)
        }

        // Execute all in parallel and wait for completion
        val futures = callables.map { callable ->
            toolExecutor.submit(callable)
        }

        // Collect results (blocks until all complete)
        val results = futures.mapIndexed { index, future ->
            try {
                future.get() // Blocks until this tool completes
            } catch (e: Exception) {
                val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
                LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
                // Create error result
                val errorMessage = when (e) {
                    is TimeoutException ->
                        "Tool `${call.name}` timed out"

                    is CancellationException ->
                        "Tool `${call.name}` was cancelled"

                    else ->
                        "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
                ToolExecutionResult(
                    call = call,
                    result = errorMessage,
                    error = e
                )
            }
        }

        // Update memory with all results
        results.forEach { result ->
            memory.add(
                "Using tool `${result.call.name}` with arguments: " +
                    result.call.arguments.map { entry ->
                        "${entry.key}=" + entry.value?.let { value ->
                            take(value.toString(), 200)
                        }
                    }.joinToString(",")
            )
            memory.add(result.result)

            // Update session log
            context.sessionLog.onToolResult(id, iteration, result.call, result.result)
        }

        LOGGER.info("$iteration $name Completed ${results.size} tool calls")
    }

    private fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim()
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

                override fun exec(input: Message, context: Context): String {
                    return "Invalid command: ${
                        input.text.split(" ").first()
                    }.\nUse /help to get the list of available commands."
                }
            }
        }
    }

    private fun exec(iteration: Int, query: Message, command: Command): String {
        val text = query.text.trim()
        val name = command.metadata().name
        val commandText = if (text.equals(name, ignoreCase = true)) {
            ""
        } else {
            text.substring(name.length).trim()
        }

        LOGGER.info("$iteration - COMMAND: {} {}", name, commandText)
        return command.exec(
            query.copy(text = commandText),
            context
        )
    }

    private fun buildPrompt(query: Message, memory: List<String>): String {
        val sb = StringBuilder()
        sb.append("Query: ${query.text}\n")

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

    private fun coordinatorInstructions(): String {
        return IOUtils.toString(Assistant::class.java.getResource("/instructions/COORDINATOR.md"), "utf-8")
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

    private fun sendToolStatus(query: Message, toolCalls: List<LLMToolCall>) {
        toolCalls.groupBy { toolCall -> toolCall.name }
            .map { entry ->
                val tool = context.toolRegistry.get(entry.key)
                val statusText = "⚙\uFE0F " + tool.statusText(entry.value)
                sendToolStatus(query, statusText)
            }
    }

    private fun sendToolStatus(query: Message, statusText: String) {
        try {
            val userId = query.userId
            val channelId = query.channelId
            if (userId != null && channelId != null) {
                val channel = context.channelRegistry.get(channelId)
                channel.sendStatus(
                    Message(
                        text = statusText,
                        role = Role.SYSTEM,
                        userId = userId,
                        channelId = channelId,
                    )
                )
            }
        } catch (e: Exception) {
            LOGGER.debug("Failed to send tool status: ${e.message}")
        }
    }
}
