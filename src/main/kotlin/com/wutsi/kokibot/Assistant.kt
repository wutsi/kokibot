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
        private const val DEFAULT_ITERATIONS = 100 // 100 iterations
        private const val BYTES_PER_TOKENS = 4
        const val DEFAULT_MAX_DURATION_MINUTES = 30L // 30 minutes
        const val DEFAULT_LANGUAGE = "en"
        const val ERROR_TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val ERROR_TIMEOUT = "Oups, the request has been cancelled because it took too much time to process."
        const val ERROR_FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private lateinit var context: Context
    private var maxIterations: Int = DEFAULT_ITERATIONS
    private var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
    private lateinit var description: String
    private var fullName: String? = null
    private var language: String? = null
    private var email: String? = null
    private var threadPoolSize: Int = 4
    internal lateinit var toolOrchestrator: ToolOrchestrator

    @Volatile
    internal lateinit var promptBuilder: PromptBuilder

    @Volatile
    internal lateinit var reasoningLoop: ReasoningLoop

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
        description = MapUtil.toString("description", config) ?: ""
        fullName = MapUtil.toString("full-name", config)
        language = MapUtil.toString("language", config) ?: DEFAULT_LANGUAGE
        email = MapUtil.toString("email", config)
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
        promptBuilder = PromptBuilder()
        reasoningLoop = ReActReasoningLoop(
            assistantName = name,
            maxIterations = maxIterations,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )

        this.context = context
        context.assistantRegistry.register(this)

        LOGGER.info("Assistant: $name")
        LOGGER.info("  full-name: $fullName")
        LOGGER.info("  language: $language")
        LOGGER.info("  email: $email")
        LOGGER.info("  max-duration: ${maxDurationMinutes}m")
        LOGGER.info("  max-iterations: $maxIterations")
        LOGGER.info("  thread-pool-size: $threadPoolSize")
    }

    fun destroy() {
        context.assistantRegistry.unregister(this)
        try {
            toolOrchestrator.destroy()
        } catch (ex: Exception) {
            LOGGER.error("Error while destroying tool orchestrator for assistant $name", ex)
        }
    }

    fun getMaxDurationMinutes(): Long = maxDurationMinutes
    fun getMaxIterations(): Int = maxIterations
    fun getDescription(): String = description
    fun getFullName(): String? = fullName
    fun getLanguage(): String? = language
    fun getEmail(): String? = email

    fun apply(key: String, value: Any) {
        when (key) {
            "max-iterations" -> {
                maxIterations = value.toString().toIntOrNull()
                    ?: throw ConfigurationException("Invalid value for max-iterations: $value")
                rebuildReasoningLoop()
            }

            "max-duration" -> {
                maxDurationMinutes = DurationUtil.minutes(value.toString(), DEFAULT_MAX_DURATION_MINUTES)
            }

            "description" -> description = value.toString()

            "full-name" -> fullName = value.toString()

            "language" -> language = value.toString()

            "email" -> email = value.toString()

            "instructions" -> setInstructions(value.toString())

            else -> throw ConfigurationException("Unknown assistant setting: $key")
        }
    }

    private fun rebuildPromptBuilder() {
        promptBuilder = PromptBuilder()
        rebuildReasoningLoop()
    }

    private fun rebuildReasoningLoop() {
        reasoningLoop = ReActReasoningLoop(
            assistantName = name,
            maxIterations = maxIterations,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator,
        )
    }

    fun contextWindow(userId: String, channelId: String, conversationId: String? = null): ContextWindow {
        val message = Message(userId = userId, channelId = channelId, conversationId = conversationId)
        val systemInstructions = promptBuilder.buildSystemInstructions(message, context)
        val prompt = promptBuilder.buildPrompt(message, emptyList(), context)
        val baseline = (systemInstructions.length + prompt.length) / BYTES_PER_TOKENS
        return ContextWindow(
            baseline = baseline,
            max = context.llm.maxContextWindow(),
        )
    }

    fun getInstructions(): String? {
        return promptBuilder.loadInstructions(context)
    }

    private fun setInstructions(content: String) {
        promptBuilder.saveInstructions(content, context)
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
