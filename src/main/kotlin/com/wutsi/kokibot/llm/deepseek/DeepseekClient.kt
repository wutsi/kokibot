package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.RestBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.json.JsonMapper

class DeepseekClient(
    val apiKey: String,
    val model: String,
    val thinking: Boolean? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val readTimeoutMillis: Long? = null,
    val connectTimeoutMillis: Long? = null,
    val restBuilder: RestBuilder = RestBuilder(),
) {
    companion object {
        const val COMPLETION_ENDPOINT = "https://api.deepseek.com/chat/completions"
        private val LOGGER = LoggerFactory.getLogger(DeepseekClient::class.java)
    }

    private val rest = restBuilder.build(readTimeoutMillis, connectTimeoutMillis)
    private val jsonMapper = JsonMapper()

    /**
     * See https://api-docs.deepseek.com/api/create-chat-completion
     */
    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        val body = toDeepseekRequest(request, tools)

        val headers = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Authorization", "Bearer $apiKey")

        val xbody = body.filter { entry -> entry.value != null }
        val entity = HttpEntity(xbody, headers)
        val resp = rest.postForEntity(
            COMPLETION_ENDPOINT,
            entity,
            Map::class.java
        ).body!!

        val response = toLLMResponse(resp)
        return response
    }

    private fun toDeepseekRequest(request: LLMRequest, tools: List<Tool>): Map<*, *> {
        return mapOf(
            "model" to model,
            "thinking" to (if (thinking == true) "enabled" else null),
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "messages" to listOfNotNull(
                mapOf(
                    "role" to "user",
                    "content" to request.prompt
                ),
                request.systemInstructions?.let { systemInstructions ->
                    mapOf(
                        "role" to "system",
                        "content" to systemInstructions
                    )
                }
            ),
            "tools" to tools.map { tool ->
                val meta = tool.metadata()
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to meta.name,
                        "description" to meta.description,
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to meta.parameters.associate { param ->
                                param.name to mapOf(
                                    "type" to param.type.name.lowercase(),
                                    "description" to param.description,
                                )
                            }.toMap(),
                        ),
                        "required" to meta.parameters
                            .filter { param -> param.required }
                            .map { param -> param.name }
                            .ifEmpty { null },
                    ).filter { entry -> entry.value != null }
                )
            }.ifEmpty { null },
            "parallel_tool_calls" to true
        )
    }

    private fun toLLMResponse(resp: Map<*, *>): LLMResponse {
        val choices = resp["choices"]!! as List<*>
        return LLMResponse(
            id = MapUtil.toString("id", resp)!!,
            choices = choices.map { choice ->
                val message = (choice as Map<*, *>)["message"] as Map<*, *>
                val toolCalls = message["tool_calls"] as List<*>?
                val finishReason = MapUtil.toString("finish_reason", choice)
                LLMResponseChoice(
                    index = MapUtil.toInt("index", choice)!!,
                    finishReason = finishReason?.let { reason -> LLMFinishReason.valueOf(reason.uppercase()) },

                    content = MapUtil.toString("content", message)!!,
                    reasoningContent = MapUtil.toString("reasoning_content", message),

                    toolCalls = toolCalls?.map { call ->
                        val function = (call as Map<*, *>)["function"] as Map<*, *>
                        val arguments = MapUtil.toString("arguments", function)

                        LLMToolCall(
                            name = MapUtil.toString("name", function)!!,
                            arguments = arguments?.let { args ->
                                try {
                                    jsonMapper.readValue(args, Map::class.java)
                                } catch (ex: Exception) {
                                    LOGGER.warn("Failed to parse tool call arguments. arguments=$args", ex)
                                    emptyMap<String, Any>()
                                }
                            } ?: emptyMap<String, Any>()
                        )
                    } ?: emptyList(),
                )
            }
        )
    }
}
