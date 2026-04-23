package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.exception.UnsupportedMimeTypeException
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.file.TextExtractorFactory
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.RestBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * This is the client for the Deepseek API.
 * It is responsible for sending requests to the Deepseek API and parsing the responses.
 */
open class DeepseekClient(
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
        const val COMPLETION_ENDPOINT = "/chat/completions"
        val EMPTY_MAP = emptyMap<String, Any>()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val rest = restBuilder.build(readTimeoutMillis, connectTimeoutMillis)
    private val jsonMapper = JsonMapper()
    private val textExtractorFactory = TextExtractorFactory()

    protected open fun getBaseUrl(): String {
        return "https://api.deepseek.com/v1"
    }

    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        val body = toDeepseekRequest(request, tools)

        val headers = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Authorization", "Bearer $apiKey")

        val entity = HttpEntity(body, headers)
        val resp = rest.postForEntity(
            getBaseUrl() + COMPLETION_ENDPOINT,
            entity,
            Map::class.java
        ).body
            ?: throw IllegalStateException("No response from LLM")

        return toLLMResponse(resp)
    }

    private fun toDeepseekRequest(request: LLMRequest, tools: List<Tool>): Map<*, *> {
        return mapOf(
            "model" to model,
            "thinking" to (if (thinking == true) "enabled" else null),
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "messages" to listOfNotNull(
                request.systemInstructions?.let { systemInstructions ->
                    mapOf(
                        "role" to "system",
                        "content" to systemInstructions
                    )
                }
            ) + toMessages(request),
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
        ).filter { entry -> entry.value != null }
    }

    private fun toLLMResponse(resp: Map<*, *>): LLMResponse {
        val choices = (resp["choices"]
            ?: throw IllegalStateException("No choices in the response")) as List<*>

        return LLMResponse(
            id = MapUtil.toString("id", resp) ?: UUID.randomUUID().toString(),
            choices = choices.mapNotNull { choice ->
                val message = MapUtil.toMap("message", (choice as Map<*, *>))
                val toolCalls = message?.get("tool_calls") as? List<*>?
                val finishReason = MapUtil.toString("finish_reason", choice)

                LLMResponseChoice(
                    index = MapUtil.toInt("index", choice) ?: 0,
                    finishReason = finishReason?.let { reason -> LLMFinishReason.valueOf(reason.uppercase()) },
                    content = message?.let { MapUtil.toString("content", message) },
                    reasoningContent = message?.let { MapUtil.toString("reasoning_content", message) },

                    toolCalls = toolCalls?.mapNotNull { call ->
                        val function = MapUtil.toMap("function", (call as Map<*, *>))
                        val arguments = function?.let { MapUtil.toString("arguments", function) }

                        function?.let {
                            LLMToolCall(
                                name = MapUtil.toString("name", function) ?: "__invalid_function__",
                                arguments = arguments?.let { args ->
                                    try {
                                        jsonMapper.readValue(args, Map::class.java)
                                    } catch (ex: Exception) {
                                        logger.warn("Failed to parse tool call arguments. arguments=$args", ex)
                                        EMPTY_MAP
                                    }
                                } ?: EMPTY_MAP
                            )
                        }
                    } ?: emptyList(),
                )
            }
        )
    }

    private fun toMessages(request: LLMRequest): List<Map<String, Any>> {
        return if (request.files.isEmpty()) {
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to request.prompt
                )
            )
        } else {
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to request.prompt
                        )
                    ) +
                        request.files.map { file ->
                            try {
                                val mimeType = getMimeType(file)
                                val content = extractContent(file, mimeType)
                                if (mimeType.startsWith("image/")) {
                                    mapOf(
                                        "type" to "image_url",
                                        "image_url" to mapOf("url" to content),
                                    )
                                } else {
                                    mapOf(
                                        "type" to "text",
                                        "text" to content,
                                    )
                                }
                            } catch (_: UnsupportedMimeTypeException) {
                                mapOf(
                                    "type" to "text",
                                    "text" to "File ${file.absolutePath} has unsupported mime type. It's content cannot be read and will be ignored."
                                )
                            } catch (ex: Exception) {
                                logger.warn("Failed to extract the content of file ${file.name}", ex)
                                mapOf(
                                    "type" to "text",
                                    "text" to "Failed to extract the content of file ${file.absolutePath}. The file will be ignored. Error: ${ex.message}"
                                )
                            }
                        }
                )
            )
        }
    }

    private fun extractContent(file: File, mimeType: String): String {
        if (mimeType == "application/json" || mimeType.startsWith("text/")) {
            return file.readText()
        } else if (mimeType.startsWith("image/")) {
            val base64Content = Base64
                .getEncoder()
                .encodeToString(file.readBytes())
            return "data:$mimeType;base64,$base64Content"
        } else {
            val extractor = textExtractorFactory.create(mimeType)
            return extractor.extract(file)
        }
    }

    private fun getMimeType(file: File): String {
        return MediaTypeFactory.getMediaType(file.name)
            .map { it.toString() }
            .orElse("application/octet-stream")
    }
}
