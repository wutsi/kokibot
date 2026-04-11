package com.wutsi.kokibot

import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.exception.CommandNotFoundException
import com.wutsi.kokibot.exception.TooManyIterationException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File

class Assistant {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val MAX_ITERATIONS = 10
        const val TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = MAX_ITERATIONS
    private lateinit var context: Context

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: MAX_ITERATIONS
        this.context = context
    }

    fun destroy() {
    }

    fun process(prompt: Message): Message {
        val response = try {
            doProcess(prompt)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }

        LOGGER.debug("answer: ${response.text}")
        if (response.role != Role.COMMAND) {
            context.chatHistory.append(prompt, response)
        }
        return response
    }

    private fun doProcess(prompt: Message): Message {
        var iteration = 0
        val memory = mutableListOf<String>()
        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            LOGGER.debug("-- ITERATION: $iteration --------------------------------------------------------------")
            val command = getCommand(prompt)
            if (command != null) {
                val result = exec(prompt, command)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                val response = ask(prompt, memory)
                if (decide(response, memory)) {
                    return Message(
                        text = response.choices.first().content,
                        role = Role.ASSISTANT,
                        finishReason = FinishReason.DONE,
                    )
                }
            }
        }
    }

    private fun ask(query: Message, memory: MutableList<String>): LLMResponse {
        val prompt = buildPrompt(query, memory)
        val systemInstructions = loadSystemInstructions()

        if (LOGGER.isDebugEnabled) {
            LOGGER.debug("LLM chat: ${query.text}")
        }

        return context.llm.completion(
            request = LLMRequest(prompt, systemInstructions)
        )
    }

    private fun decide(response: LLMResponse, memory: MutableList<String>): Boolean {
        // Tool calls
        val choiceCalls = response.choices.filter { choice -> choice.toolCalls.isNotEmpty() }
        if (choiceCalls.isNotEmpty()) {
            LOGGER.debug("FUNCTION CALLS")
            choiceCalls.forEach { choice -> exec(choice, memory) }
            return false
        } else {
            return true
        }
    }

    private fun exec(choice: LLMResponseChoice, memory: MutableList<String>) {
        LOGGER.debug(choice.content)

        choice.toolCalls.forEach {
            exec(choice.content, it, memory)
        }
    }

    private fun exec(content: String, call: LLMToolCall, memory: MutableList<String>) {
        LOGGER.debug("Tool execution: name={} - arguments={}", call.name, call.arguments)

        val tool = context.toolRegistry.get(call.name)
        val result = tool.exec(call.arguments)

        memory.add(content)
        memory.add("Calling the tool `${call.name}` returned the following result: $result")
    }

    private fun getCommand(query: Message): Command? {
        val text = query.text.trim()
        if (!text.startsWith("/")) {
            return null
        }

        val name = text.split(" ")[0]
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

        LOGGER.debug("Command execution: name={} - input={}", name, input)
        return command.exec(input, context)
    }

    private fun buildPrompt(prompt: Message, memory: List<String>): String {
        val sb = StringBuilder()
        sb.append("Query: ${prompt.text}")

        if (memory.isNotEmpty()) {
            sb.append("\n\n# Previous reasoning steps and observations")
            memory.forEach { line -> sb.append("$line\n\n") }
        }

        val json = context.chatHistory.get()
        if (json != null) {
            sb.append("\n\n# Conversation history\n")
            sb.append("Here is the conversation history between you and the user in JSON format:\n")
            sb.append("```json\n$json\n```\n")
        }

        val memory = context.memory.get()
        if (memory != null) {
            sb.append("\n\n# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$memory\n```\n")
        }
        return sb.toString()
    }

    private fun loadSystemInstructions(): String? {
        val file = File(context.home, "AGENT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }
}
