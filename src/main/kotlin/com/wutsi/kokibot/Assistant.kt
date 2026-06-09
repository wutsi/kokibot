package com.wutsi.kokibot

import com.wutsi.kokibot.assistant.PromptBuilder
import com.wutsi.kokibot.assistant.ToolOrchestrator
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.user.AskQuestionException
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
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
    private lateinit var toolOrchestrator: ToolOrchestrator
    private lateinit var promptBuilder: PromptBuilder

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
        toolOrchestrator = ToolOrchestrator(threadPoolSize = threadPoolSize)
        promptBuilder = PromptBuilder(assistantName = name)

        this.context = context
        context.assistantRegistry.register(this)

        LOGGER.info("Assistant: $name")
        LOGGER.info("  coordinator: $coordinator")
        LOGGER.info("  max-duration: ${maxDurationMinutes}m")
        LOGGER.info("  max-iterations: $maxIterations")
        LOGGER.info("  thread-pool-size: $threadPoolSize")
    }

    fun destroy() {
        if (::toolOrchestrator.isInitialized) {
            LOGGER.info("Shutting down tool orchestrator for assistant: $name")
            toolOrchestrator.destroy()
        }
    }

    fun contextLength(userId: String?, channelId: String?): Int {
        val query = Message(
            userId = userId,
            channelId = channelId,
        )
        return promptBuilder.buildPrompt(query, emptyList(), context).length +
            promptBuilder.buildSystemInstructions(query, coordinator, context).length
    }

    fun process(
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        // Restore session if exists
        val now = System.currentTimeMillis()
        val sessionId = context.sessionLog.resume(query.userId, query.channelId)
        val sessions = sessionId?.let {
            context.sessionLog.get(sessionId)
        }

        // Restore execution context
        var xquery = query
        var iteration = 0
        var memory = mutableListOf<String>()
        if (sessions != null && sessions.isNotEmpty()) {
            // Resume processing
            xquery = query.copy(
                id = sessionId,
                text = sessions.first().content.firstOrNull { content -> content.type == "text" }?.text ?: query.text,
                filePaths = sessions.first().content.filter { content -> content.type == "file" }
                    .mapNotNull { content -> content.text }
            )
            memory = sessions.lastOrNull { session -> session.memory != null && session.memory.isNotEmpty() }
                ?.memory
                ?.toMutableList()
                ?: mutableListOf()
            memory.add(query.text)

            iteration = sessions.last { session -> session.iteration != null }
                .iteration ?: 0
        }

        LOGGER.info(
            "${xquery.id} $name ${xquery.userId ?: "-"}@${xquery.channelId ?: "-"} files=${xquery.filePaths} " +
                take(xquery.text, 200)
        )
        context.sessionLog.onQuery(xquery.id, iteration, xquery)

        // Push to delegation stack
        context.delegationStack.push(xquery.id, name, streamCallback)
        val response = try {
            // Process async
            val timer = Executors.newSingleThreadExecutor()
            val future = timer.submit<Message> {
                doProcessAsync(query, streamCallback, iteration, memory)
            }

            // Wait for the response with timeout
            try {
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
            }
        } catch (e: Exception) {
            LOGGER.error("Delegation stack push failed for $name", e)
            Message("Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        } finally {
            // Pop from delegation stack
            context.delegationStack.pop(query.id)
        }

        // Result
        val duration = DurationUtil.hms(System.currentTimeMillis() - now)
        LOGGER.info(
            "${query.id} $name FINAL ANSWER ($duration): " + take(response.text, 200)
        )
        context.chatHistory.append(query, response)
        context.sessionLog.onResponse(query.id, response)
        return response
    }

    private fun doProcessAsync(
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
        iteration: Int,
        memory: MutableList<String>,
    ): Message {
        return try {
            doProcess(query, streamCallback, iteration, memory)
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
        iter: Int,
        memory: MutableList<String>,
    ): Message {
        var iteration = iter
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
                try {
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
                } catch (ex: AskQuestionException) {
                    // Pause the current session
                    context.sessionLog.pause(query.userId, query.channelId, query.id)

                    // Return the question to ask to the user, the session will be resumed when the user answers
                    return Message(
                        text = ex.question,
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
            prompt = promptBuilder.buildPrompt(query, memory, context),
            systemInstructions = promptBuilder.buildSystemInstructions(query, coordinator, context),
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

        // Execute all tool calls in parallel using ToolOrchestrator
        toolOrchestrator.executeTools(
            id = id,
            iteration = iteration,
            assistantName = name,
            toolCalls = allToolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )
        return false
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

}
