package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMBalance
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.llm.LLMToolCallDelta
import com.wutsi.kokibot.llm.LLMUsage
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import com.wutsi.kokibot.util.RestBuilder
import com.wutsi.kokibot.util.retry.Retrier
import com.wutsi.kokibot.util.retry.RetryClassifier
import com.wutsi.kokibot.util.retry.RetryPolicy
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This is the client for the Deepseek API.
 * It is responsible for sending requests to the Deepseek API and parsing the responses.
 *
 * API Documentations:
 * - chat completions: https://api-docs.deepseek.com/api/create-chat-completion
 */
open class DeepseekClient(
    val apiKey: String,
    val model: String,
    protected val thinking: Boolean? = null,
    val reasoningEffort: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val readTimeoutMillis: Long? = null,
    val connectTimeoutMillis: Long? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.default(),
    val restBuilder: RestBuilder = RestBuilder(),
    val jsonMapper: JsonMapper = JsonMapper(),
    val responseFormat: String? = null,
) {
    companion object {
        private val EMPTY_MAP = emptyMap<String, Any>()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val rest = restBuilder.build(readTimeoutMillis, connectTimeoutMillis)
    private val retrier = Retrier(policy = retryPolicy, logger = logger)

    protected open fun getBaseUrl(): String {
        return "https://api.deepseek.com"
    }

    protected open fun getCompletionUrl(): String {
        return getBaseUrl() + "/chat/completions"
    }

    protected open fun getBalanceUrl(): String? {
        return getBaseUrl() + "/user/balance"
    }

    protected open fun supportsMimeType(mimeType: String): Boolean {
        return false
    }

    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        val body = toDeepseekRequest(request, tools)

        val headers = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Authorization", "Bearer $apiKey")

        val entity = HttpEntity(body, headers)
        val resp = retrier.execute(operationName = "${this::class.java.simpleName}.completion") {
            rest.postForEntity(
                getCompletionUrl(),
                entity,
                Map::class.java
            ).body
        } ?: throw IllegalStateException("No response from LLM")

        return toLLMResponse(resp)
    }

    /**
     * Streaming completion using SSE (Server-Sent Events).
     * Uses RestTemplate.execute() for low-level stream access.
     */
    fun completionStream(
        request: LLMRequest,
        tools: List<Tool>,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse {
        val body = toDeepseekRequest(request, tools)
        val streamBody = body.toMutableMap().apply {
            put("stream", true)
            put("stream_options", mapOf("include_usage" to true))
        }

        val headers = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Authorization", "Bearer $apiKey")
        headers.setAccept(listOf(MediaType.TEXT_EVENT_STREAM))

        // Retry only the initiation of the stream. Once we forward a chunk to
        // `onChunk`, we cannot replay deltas safely, so refuse any further retry.
        val firstChunkEmitted = AtomicBoolean(false)
        val guardedOnChunk: (LLMStreamChunk) -> Unit = { chunk ->
            firstChunkEmitted.set(true)
            onChunk(chunk)
        }

        return retrier.execute(
            operationName = "deepseek.completionStream",
            shouldRetry = { ex -> !firstChunkEmitted.get() && RetryClassifier.isRetryable(ex) },
        ) {
            rest.execute(
                getCompletionUrl(),
                org.springframework.http.HttpMethod.POST,
                { clientRequest ->
                    clientRequest.headers.putAll(headers)
                    clientRequest.body.write(
                        jsonMapper.writeValueAsBytes(streamBody)
                    )
                },
                { clientResponse ->
                    parseSSEStream(clientResponse.body, guardedOnChunk)
                }
            )
        } ?: throw IllegalStateException("No response from streaming LLM")
    }

    /**
     * See https://api-docs.deepseek.com/api/get-user-balance
     */
    fun balance(): LLMBalance? {
        val url = getBalanceUrl() ?: return null
        val headers = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Authorization", "Bearer $apiKey")

        val resp = retrier.execute(operationName = "${this::class.java.simpleName}.balance") {
            rest.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                Map::class.java,
                EMPTY_MAP
            )
        }.body ?: throw IllegalStateException("No response from LLM")

        return toLLMBalance(resp as Map<String, *>)
    }

    protected open fun toLLMBalance(resp: Map<String, *>): LLMBalance? {
        val available = MapUtil.toBoolean("is_available", resp) ?: false
        if (!available) {
            return null
        }

        return MapUtil.toList("balance_infos", resp)?.let { infos ->
            val info = infos.firstOrNull()
            if (info is Map<*, *>) {
                return LLMBalance(
                    currency = MapUtil.toString("currency", info) ?: "USD",
                    total = MapUtil.toDouble("total_balance", info) ?: 0.0,
                )
            } else {
                null
            }
        }
    }

    protected open fun toLLMResponse(resp: Map<*, *>): LLMResponse {
        val choices = (resp["choices"]
            ?: throw IllegalStateException("No choices in the response")) as List<*>
        val usage = MapUtil.toMap("usage", resp)

        return LLMResponse(
            id = MapUtil.toString("id", resp) ?: UUID.randomUUID().toString(),
            model = resp["model"] as? String,
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
                                id = MapUtil.toString("id", function) ?: UUID.randomUUID().toString(),
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
            },
            usage = usage?.let { toLLMUsage(usage) }
        )
    }

    protected open fun toLLMUsage(usage: Map<*, *>): LLMUsage {
        return LLMUsage(
            promptTokens = MapUtil.toInt("prompt_tokens", usage) ?: 0,
            completionTokens = MapUtil.toInt("completion_tokens", usage) ?: 0,
            totalTokens = MapUtil.toInt("total_tokens", usage) ?: 0,
            promptCacheHitTokens = MapUtil.toInt("prompt_cache_hit_tokens", usage),
        )
    }

    /**
     * Parses Server-Sent Events stream from LLM API.
     *
     * SSE Format:
     * data: {"choices":[{"delta":{"content":"Hello"}}]}
     *
     * data: {"choices":[{"delta":{"content":" world"}}]}
     *
     * data: [DONE]
     */
    private fun parseSSEStream(
        inputStream: java.io.InputStream,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse {
        val reader = inputStream.bufferedReader()
        val accumulator = StreamResponseAccumulator(jsonMapper)

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                when {
                    line?.startsWith("data: ") == true -> {
                        val data = line.substring(6).trim()
                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val jsonChunk = jsonMapper.readValue(data, Map::class.java)
                            val chunk = parseStreamChunk(jsonChunk)
                            onChunk(chunk)
                            accumulator.add(chunk)
                        } catch (ex: Exception) {
                            logger.warn("Failed to parse stream chunk: $data", ex)
                        }
                    }

                    else -> {
                    }
                }
            }
        } finally {
            reader.close()
        }

        return accumulator.toResponse()
    }

    /**
     * Parses a single SSE chunk into LLMStreamChunk.
     * Handles both content deltas and reasoning deltas (DeepSeek V4).
     */
    private fun parseStreamChunk(jsonChunk: Map<*, *>): LLMStreamChunk {
        val choices = jsonChunk["choices"] as? List<*> ?: emptyList<Any>()
        val firstChoice = choices.firstOrNull() as? Map<*, *>
        val delta = firstChoice?.get("delta") as? Map<*, *>
        val usage = MapUtil.toMap("usage", jsonChunk)

        return LLMStreamChunk(
            delta = delta?.get("content") as? String,
            reasoningDelta = delta?.get("reasoning_content") as? String,
            toolCallDelta = parseToolCallDeltaFromDelta(delta),
            finishReason = (firstChoice?.get("finish_reason") as? String)
                ?.let { LLMFinishReason.valueOf(it.uppercase()) },
            isDone = firstChoice?.get("finish_reason") != null,
            usage = usage?.let { toLLMUsage(usage) }
        )
    }

    /**
     * Parses a partial tool call from a streaming delta.
     *
     * In OpenAI-compatible streaming, tool calls arrive across multiple chunks:
     *  - First chunk: `index`, `id`, `function.name`
     *  - Subsequent chunks: only `function.arguments` fragments (raw JSON text)
     *
     * Fragments are not necessarily valid JSON on their own and MUST be
     * concatenated by [LLMToolCallDelta.index] before parsing. The actual
     * parsing happens in [StreamResponseAccumulator].
     */
    private fun parseToolCallDeltaFromDelta(delta: Map<*, *>?): LLMToolCallDelta? {
        val toolCalls = delta?.get("tool_calls") as? List<*> ?: return null
        val firstCall = toolCalls.firstOrNull() as? Map<*, *> ?: return null
        val function = firstCall["function"] as? Map<*, *>

        return LLMToolCallDelta(
            index = (firstCall["index"] as? Number)?.toInt() ?: 0,
            id = firstCall["id"] as? String,
            name = function?.get("name") as? String,
            argumentsFragment = function?.get("arguments") as? String,
        )
    }

    private fun toDeepseekRequest(request: LLMRequest, tools: List<Tool>): Map<*, *> {
        return mapOf(
            "model" to model,
            "thinking" to if (toThinking(request)) {
                mapOf(
                    "type" to "enabled",
                )
            } else {
                mapOf(
                    "type" to "disabled",
                )
            },
            "reasoning_effort" to toReasoningEffort(request),
            "max_tokens" to maxTokens,
            "response_format" to responseFormat?.let {
                mapOf(
                    "type" to if (it.contains("json", true)) "json_object" else "text",
                )
            },
            "temperature" to toTemperature(request),
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

    internal open fun toThinking(request: LLMRequest): Boolean {
        return thinking ?: false
    }

    internal open fun toTemperature(request: LLMRequest): Double? {
        return temperature
    }

    internal open fun toReasoningEffort(request: LLMRequest): String? {
        if (toThinking(request) == false) {
            return null
        }

        return when (reasoningEffort?.lowercase()) {
            null -> null
            "max" -> "max"
            else -> "high"
        }
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
                            val mimeType = getMimeType(file)
                            val bytes = file.readBytes()
                            if (supportsMimeType(mimeType)) {
                                val base64Content = Base64
                                    .getEncoder()
                                    .encodeToString(bytes)
                                val content = "data:$mimeType;base64,$base64Content"

                                if (mimeType.startsWith("image/")) {
                                    mapOf(
                                        "type" to "image_url",
                                        "image_url" to mapOf("url" to content),
                                    )
                                } else {
                                    mapOf(
                                        "type" to "file",
                                        "file" to mapOf(
                                            "filename" to file.name,
                                            "file_data" to content,
                                        )
                                    )
                                }
                            } else {
                                mapOf(
                                    "type" to "text",
                                    "text" to "File: ${file.absolutePath}"
                                )
                            }
                        }
                )
            )
        }
    }

    private fun getMimeType(file: File): String {
        return MediaTypeFactory.getMediaType(file.name)
            .map { it.toString() }
            .orElse("application/octet-stream")
    }
}
