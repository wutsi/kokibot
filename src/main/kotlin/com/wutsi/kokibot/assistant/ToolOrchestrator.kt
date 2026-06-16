package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.ToolExecutionResult
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.ExecutionContext
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.user.AskQuestionException
import com.wutsi.kokibot.util.StringUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ToolOrchestrator(
    private val threadPoolSize: Int = 4
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ToolOrchestrator::class.java)
    }

    private val toolExecutor: ExecutorService = Executors.newFixedThreadPool(threadPoolSize)

    fun destroy() {
        LOGGER.info("Shutting down tool executor")
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

    fun executeTools(
        id: String,
        iteration: Int,
        assistantName: String,
        toolCalls: List<LLMToolCall>,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ) {
        if (toolCalls.isEmpty()) {
            return
        }

        // Get results
        val results = if (toolCalls.size == 1) {
            listOf(
                single(
                    id = id,
                    iteration = iteration,
                    assistantName = assistantName,
                    call = toolCalls[0],
                    tools = tools,
                    query = query,
                    context = context
                )
            )
        } else {
            parallel(
                id = id,
                iteration = iteration,
                assistantName = assistantName,
                toolCalls = toolCalls,
                tools = tools,
                query = query,
                context = context
            )
        }

        // Update memory
        results.forEach { result ->
            if (result.error is AskQuestionException) {
                throw result.error
            }

            memory.add(
                "Using tool `${result.call.name}` with arguments: " +
                    result.call.arguments.map { entry ->
                        "${entry.key}=" + entry.value?.let { value ->
                            StringUtil.take(value.toString(), 200)
                        }
                    }.joinToString(",")
            )
            memory.add(result.result)

            context.sessionLog.onToolResult(id, iteration, result.call, result.result)
        }

        LOGGER.info("$iteration $assistantName Completed ${results.size} tool calls")
    }

    private fun single(
        id: String,
        iteration: Int,
        assistantName: String,
        call: LLMToolCall,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): ToolExecutionResult {
        LOGGER.info("$iteration $assistantName Executing 1 tool calls")

        sendToolStatus(query, call, context)

        val callable = createToolCallable(id, iteration, assistantName, call, tools, query, context)
        return callable.call()
    }

    private fun parallel(
        id: String,
        iteration: Int,
        assistantName: String,
        toolCalls: List<LLMToolCall>,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): List<ToolExecutionResult> {
        LOGGER.info("$iteration $assistantName Executing ${toolCalls.size} tool calls in parallel")

        sendToolStatus(query, toolCalls, context)

        val callables = toolCalls.map { call ->
            createToolCallable(id, iteration, assistantName, call, tools, query, context)
        }

        val futures = callables.map { callable ->
            toolExecutor.submit(callable)
        }

        return futures.mapIndexed { index, future ->
            try {
                future.get()
            } catch (e: Exception) {
                val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
                LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
                val errorMessage = when (e) {
                    is TimeoutException -> "Tool `${call.name}` timed out"
                    is CancellationException -> "Tool `${call.name}` was cancelled"
                    else -> "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
                ToolExecutionResult(call = call, result = errorMessage, error = e)
            }
        }
    }

    private fun createToolCallable(
        id: String,
        iteration: Int,
        assistantName: String,
        call: LLMToolCall,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): Callable<ToolExecutionResult> {
        return Callable {
            ExecutionContext.set(id, assistantName, query.userId, query.channelId)
            val startTime = System.currentTimeMillis()
            LOGGER.info(
                "$iteration $assistantName TOOL ${call.name} " +
                    call.arguments.map { entry ->
                        "${entry.key}=" + entry.value?.let { value -> StringUtil.take(value.toString(), 200) }
                    }.joinToString(",")
            )
            context.sessionLog.onToolUse(id, iteration, call)

            var exception: Exception? = null
            val result = tools[call.name]?.let { tool ->
                try {
                    tool.exec(call.arguments)
                } catch (e: Exception) {
                    exception = e
                    val duration = System.currentTimeMillis() - startTime
                    LOGGER.warn("Unexpected error while executing tool `${call.name}` after ${duration}ms. Error=${e.message}")
                    "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
            }
            ToolExecutionResult(
                call = call,
                result = result ?: "Tool `${call.name}` not found",
                error = exception,
            )
        }
    }

    private fun sendToolStatus(query: Message, call: LLMToolCall, context: Context) {
        sendToolStatus(query, listOf(call), context)
    }

    private fun sendToolStatus(query: Message, toolCalls: List<LLMToolCall>, context: Context) {
        toolCalls.groupBy { toolCall -> toolCall.name }
            .forEach { entry ->
                try {
                    val tool = context.toolRegistry.get(entry.key)
                    val statusText = "⚙️ " + tool.statusText(entry.value)
                    sendToolStatus(query, statusText, context)
                } catch (e: Exception) {
                    LOGGER.debug("Failed to send tool status for ${entry.key}: ${e.message}")
                }
            }
    }

    private fun sendToolStatus(query: Message, statusText: String, context: Context) {
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
    }
}
