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
import java.util.concurrent.ConcurrentHashMap

class SessionLog : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SessionLog::class.java)
    }

    private val pausedSessionId = ConcurrentHashMap<String, String>()
    private lateinit var context: Context

    override fun id(): String {
        return "session-log"
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun onQuery(sessionId: String, iteration: Int, prompt: Message) {
        append(
            sessionId,
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

    fun onResponse(sessionId: String, message: Message) {
        append(
            sessionId,
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

    fun onToolUse(sessionId: String, iteration: Int, tool: LLMToolCall) {
        append(
            sessionId,
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

    fun onToolResult(sessionId: String, iteration: Int, tool: LLMToolCall, result: String) {
        append(
            sessionId,
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

    fun onLLMResponse(sessionId: String, iteration: Int, response: LLMResponse, memory: List<String>) {
        append(
            sessionId,
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

    fun get(sessionId: String): List<Session> {
        val file = getFile(sessionId)
        if (!file.exists()) {
            return emptyList()
        }

        return file.readText().lines().mapIndexedNotNull { i, line ->
            try {
                if (line.isNotEmpty()) {
                    context.jsonMapper.readValue(line, Session::class.java)
                } else {
                    null
                }
            } catch (ex: Exception) {
                LOGGER.warn("Line ${i + 1} - Failure: $line", ex)
                null
            }
        }
    }

    fun pause(userId: String?, channelId: String?, sessionId: String) {
        val key = pauseSessionKey(userId, channelId)
        pausedSessionId[key] = sessionId
    }

    fun resume(userId: String?, channelId: String?): String? {
        val key = pauseSessionKey(userId, channelId)
        return pausedSessionId.remove(key)
    }

    private fun pauseSessionKey(userId: String?, channelId: String?): String {
        return ((userId ?: "-") + "_" + (channelId ?: "-")).lowercase()
    }

    @Synchronized
    private fun append(sessionId: String, session: Session) {
        val file = getFile(sessionId)
        file.appendText(context.jsonMapper.writeValueAsString(session) + "\n")
    }

    private fun getFile(sessionId: String): File {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val dir = File(context.home.absolutePath + "/memory/sessions/$today")
        dir.mkdirs()
        return File(dir, "$sessionId.jsonl")
    }
}
