package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMToolCall
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SessionLog : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SessionLog::class.java)
    }

    private lateinit var context: Context

    override fun id(): String {
        return "session-log"
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun onQuery(id: String, iteration: Int, prompt: Message) {
        append(
            id,
            Session(
                iteration = iteration,
                role = prompt.role,
                userId = prompt.userId,
                channelId = prompt.channelId,
                content = listOf(
                    SessionContent(
                        type = "text",
                        text = prompt.text,
                    )
                ) +
                    prompt.filePaths.map { path ->
                        SessionContent(
                            type = "file",
                            text = path,
                        )
                    }
            )
        )
    }

    fun onResponse(id: String, message: Message) {
        append(
            id,
            Session(
                role = message.role,
                userId = message.userId,
                channelId = message.channelId,
                content = listOf(
                    SessionContent(
                        type = "text",
                        text = message.text,
                    ),
                    SessionContent(
                        type = "text",
                        text = "DONE",
                    )
                )
            )
        )
    }

    fun onToolUse(id: String, iteration: Int, tool: LLMToolCall) {
        append(
            id,
            Session(
                iteration = iteration,
                role = Role.TOOL_USE,
                content = listOf(
                    SessionContent(
                        type = "tool_use",
                        id = tool.id,
                        name = tool.name,
                        arguments = tool.arguments.map { it.key.toString() to it.value.toString() }.toMap(),
                    )
                )
            )
        )
    }

    fun onToolResult(id: String, iteration: Int, tool: LLMToolCall, result: String) {
        append(
            id,
            Session(
                iteration = iteration,
                role = Role.TOOL_RESULT,
                content = listOf(
                    SessionContent(
                        type = "tool_result",
                        id = tool.id,
                        text = result,
                    )
                )
            )
        )
    }

    fun onLLMResponse(id: String, iteration: Int, response: LLMResponse, memory: List<String>) {
        append(
            id,
            Session(
                iteration = iteration,
                role = Role.ASSISTANT,
                model = response.model,
                usage = response.usage,
                memory = memory,
                content = response.choices.flatMap { choice ->
                    listOfNotNull(
                        choice.reasoningContent?.let { content ->
                            SessionContent(
                                type = "thinking",
                                text = content,
                            )
                        },
                        choice.content?.let { content ->
                            SessionContent(
                                type = "text",
                                text = content,
                            )
                        },
                    ) +
                        choice.toolCalls.map { call ->
                            SessionContent(
                                type = "tool",
                                name = call.name,
                                arguments = call.arguments.map { it.key.toString() to it.value.toString() }.toMap(),
                            )
                        }
                }
            )
        )
    }

    fun get(id: String): List<Session> {
        val file = getFile(id)
        if (!file.exists()) {
            return emptyList()
        }

        return file.readText().lines().mapIndexedNotNull { i, line ->
            try {
                context.jsonMapper.readValue(line, Session::class.java)
            } catch (ex: Exception) {
                LOGGER.warn("Line ${i + 1} - Failure", ex)
                null
            }
        }
    }

    private fun append(id: String, session: Session) {
        val file = getFile(id)
        file.appendText(context.jsonMapper.writeValueAsString(session) + "\n")
    }

    private fun getFile(id: String): File {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val dir = File(context.home.absolutePath + "/memory/sessions/$today")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$id.jsonl")
    }
}
