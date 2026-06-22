package com.wutsi.kokibot

import com.wutsi.kokibot.assistant.ContextWindow
import com.wutsi.kokibot.assistant.PromptBuilder
import com.wutsi.kokibot.assistant.ReActReasoningLoop
import com.wutsi.kokibot.assistant.ReasoningLoop
import com.wutsi.kokibot.assistant.ToolOrchestrator
import com.wutsi.kokibot.llm.LLMStreamData
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.StringUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Assistant(val name: String = "") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val DEFAULT_ITERATIONS = 10
        private const val BYTES_PER_TOKENS = 4
        const val DEFAULT_MAX_DURATION_MINUTES = 5L
        const val ERROR_TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val ERROR_TIMEOUT = "Oups, the request has been cancelled because it took too much time to process."
        const val ERROR_FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private lateinit var context: Context
    private var maxIterations: Int = DEFAULT_ITERATIONS
    private var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
    lateinit var description: String
    var coordinator: Boolean = false
    private var threadPoolSize: Int = 4
    private lateinit var toolOrchestrator: ToolOrchestrator
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var reasoningLoop: ReasoningLoop

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
        reasoningLoop = ReActReasoningLoop(
            assistantName = name,
            maxIterations = maxIterations,
            coordinator = coordinator,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )

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

    fun contextWindow(userId: String, channelId: String, conversationId: String? = null): ContextWindow {
        val message = Message(userId = userId, channelId = channelId, conversationId = conversationId)
        val systemInstructions = promptBuilder.buildSystemInstructions(message, coordinator, context)
        val prompt = promptBuilder.buildPrompt(message, emptyList(), context)
        val baseline = (systemInstructions.length + prompt.length) / BYTES_PER_TOKENS
        return ContextWindow(
            baseline = baseline,
            max = context.llm.maxContextWindow(),
        )
    }

    fun getInstructions(): String? {
        return promptBuilder.loadIdentity(context)
    }

    fun saveInstructions(content: String) {
        promptBuilder.saveIdentity(content, context)
    }

    fun process(
        query: Message,
        streamCallback: ((LLMStreamData) -> Unit)? = null,
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
                StringUtil.take(xquery.text, 200)
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
            "${query.id} $name FINAL ANSWER ($duration): " + StringUtil.take(response.text, 200)
        )
        val conversationId = context.chatHistory.append(query, response)
        context.sessionLog.onResponse(query.id, response)
        return response.copy(conversationId = conversationId)
    }

    private fun doProcessAsync(
        query: Message,
        streamCallback: ((LLMStreamData) -> Unit)? = null,
        iteration: Int,
        memory: MutableList<String>,
    ): Message {
        return try {
            reasoningLoop.execute(query, streamCallback, iteration, memory, context)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(ERROR_TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }
    }
}
