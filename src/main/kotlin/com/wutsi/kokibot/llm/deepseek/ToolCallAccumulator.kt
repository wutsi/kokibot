package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.llm.LLMToolCallDelta
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class ToolCallAccumulator(private val jsonMapper: JsonMapper) {
    companion object {
        private val EMPTY_ARGS = emptyMap<String, Any>()
        private val LOGGER: Logger = LoggerFactory.getLogger(ToolCallAccumulator::class.java)
    }

    private var id: String? = null
    private var name: String? = null
    private val arguments = StringBuilder()
    private var complete: LLMToolCall? = null

    fun merge(delta: LLMToolCallDelta) {
        if (!delta.id.isNullOrEmpty()) id = delta.id
        if (!delta.name.isNullOrEmpty()) name = delta.name
        delta.argumentsFragment?.let { arguments.append(it) }
    }

    fun setComplete(call: LLMToolCall) {
        complete = call
    }

    fun build(jsonMapper: JsonMapper): LLMToolCall? {
        complete?.let { return it }
        val fnName = name ?: return null
        val rawArgs = arguments.toString()
        val parsedArgs: Map<*, *> = if (rawArgs.isBlank()) {
            EMPTY_ARGS
        } else {
            try {
                jsonMapper.readValue(rawArgs, Map::class.java)
            } catch (ex: Exception) {
                LOGGER.warn("Failed to parse accumulated tool-call arguments. tool=$name arguments=$rawArgs", ex)
                EMPTY_ARGS
            }
        }
        return LLMToolCall(name = fnName, arguments = parsedArgs, id = id ?: UUID.randomUUID().toString()).apply {}
    }
}
