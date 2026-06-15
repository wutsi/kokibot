package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.FinishReason
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.TooManyIterationException
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMStreamData
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.user.AskQuestionException
import com.wutsi.kokibot.util.MarkdownUtil
import com.wutsi.kokibot.util.StringUtil
import com.wutsi.kokibot.util.StringUtil.take
import org.slf4j.LoggerFactory
import java.io.File

/**
 * ReAct (Reasoning + Acting) reasoning loop implementation.
 *
 * This implements the ReAct pattern where the LLM:
 * 1. Reasons about the task
 * 2. Decides which tools to call (Acting)
 * 3. Observes tool results
 * 4. Repeats until task is complete
 */
class ReActReasoningLoop(
    private val assistantName: String,
    private val maxIterations: Int,
    private val coordinator: Boolean,
    private val promptBuilder: PromptBuilder,
    private val toolOrchestrator: ToolOrchestrator
) : ReasoningLoop {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReActReasoningLoop::class.java)
    }

    override fun execute(
        query: Message,
        streamCallback: ((LLMStreamData) -> Unit)?,
        startIteration: Int,
        memory: MutableList<String>,
        context: Context
    ): Message {
        var iteration = startIteration
        val tools = mutableMapOf<String, Tool>()
        context.toolRegistry.all().forEach { tool -> tools[tool.metadata().name] = tool }

        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            val command = getCommand(query, context)
            if (command != null) {
                val result = exec(iteration, query, command, context)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                try {
                    val response = ask(iteration, query, memory, streamCallback, context)
                    if (decide(query.id, iteration, response, memory, tools, query, context)) {
                        return Message(
                            text = response.choices.mapNotNull { choice -> choice.content }.joinToString("\n\n"),
                            role = Role.ASSISTANT,
                            finishReason = FinishReason.DONE,
                        )
                    } else {
                        if (streamCallback != null) {
                            response.choices.forEach { choice ->
                                streamCallback(
                                    LLMStreamData(
                                        text = choice.content?.let { content ->
                                            take(MarkdownUtil.toText(content), 1024)
                                        } ?: "",
                                        usage = response.usage
                                    )
                                )
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
        streamCallback: ((LLMStreamData) -> Unit)?,
        context: Context
    ): LLMResponse {
        LOGGER.info("$iteration $assistantName LLM " + StringUtil.take(query.text, 200))

        // Call LLM
        val request = LLMRequest(
            prompt = promptBuilder.buildPrompt(query, memory, context),
            systemInstructions = promptBuilder.buildSystemInstructions(query, coordinator, context),
            files = query.filePaths.map { path -> File(path) }
        )

        val tools = context.toolRegistry.all().filter { tool -> tool.activate() }
        val streamingEnabled = context.llm.supportsStreaming()
        val response = if (streamingEnabled && streamCallback != null) {
            context.llm.completionStream(
                request = request,
                tools = tools,
                onChunk = { chunk ->
                    chunk.reasoningDelta?.let { delta ->
                        streamCallback(
                            LLMStreamData(
                                text = delta,
                                usage = null // Usage only available at end of stream
                            )
                        )
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
        LOGGER.info("$iteration $assistantName LLM - tokens=" + response.usage?.totalTokens + ", cached=" + response.usage?.promptCacheHitTokens)
        context.sessionLog.onLLMResponse(query.id, iteration, response, memory)

        // Update memory with reasoning content
        response.choices.forEach { choice ->
            if (!choice.content.isNullOrEmpty()) {
                LOGGER.info(StringUtil.take(choice.content, 200))
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
        context: Context
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
            assistantName = assistantName,
            toolCalls = allToolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )
        return false
    }

    private fun getCommand(query: Message, context: Context): Command? {
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

    private fun exec(iteration: Int, query: Message, command: Command, context: Context): String {
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
