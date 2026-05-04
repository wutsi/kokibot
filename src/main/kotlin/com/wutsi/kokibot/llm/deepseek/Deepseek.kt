package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 *
 * API Documentations:
 * - chat completions: https://api-docs.deepseek.com/api/create-chat-completion
 */
open class Deepseek : LLM {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Deepseek::class.java)
        const val CONNECT_TIMEOUT_MILLIS = 5000L
        const val READ_TIMEOUT_MILLIS = 60000L
    }

    private lateinit var context: Context
    internal lateinit var client: DeepseekClient
    internal var streamingEnabled: Boolean = false

    override fun id(): String {
        return "llm:deepseek"
    }

    /**
     * Initialize the Deepseek client with the given configuration and context.
     * The configuration can contain the following parameters:
     * - api-key: the API key for Deepseek (required)
     * - model: the model to use for generation. Values: deepseek-chat, deepseek-reasoner (required)
     * - thinking: `true` to enable thinking mode, `false` to disable (default: false)
     * - max-tokens: the maximum number of tokens to generate
     * - temperature: the temperature to use for generation
     * - read-timeout-millis: the read timeout in milliseconds (default: 60000)
     * - connect-timeout-millis: the connect timeout in milliseconds (default: 5000)
     */
    override fun init(config: Map<*, *>, context: Context) {
        val apiKey = config["api-key"] as String? ?: throw ConfigurationException("api-key is required")
        val model = config["model"] as String? ?: throw ConfigurationException("model is required")

        this.context = context
        this.streamingEnabled = MapUtil.toBoolean("streaming", config) ?: false
        this.client = createClient(apiKey, model, config)

        LOGGER.info("LLM: " + config["type"])
        LOGGER.info(" model: $model")
        LOGGER.info(" streaming: $streamingEnabled")
    }

    override fun health(): Health {
        return try {
            client.completion(LLMRequest(prompt = "Hello"), emptyList())
            Health(id = id())
        } catch (ex: Exception) {
            Health(id = id(), up = false, details = ex.message ?: "unknown error")
        }
    }

    override fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        return client.completion(request, tools)
    }

    override fun supportsStreaming(): Boolean {
        return streamingEnabled
    }

    override fun completionStream(
        request: LLMRequest,
        tools: List<Tool>,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse {
        return client.completionStream(request, tools, onChunk)
    }

    protected open fun createClient(apiKey: String, model: String, config: Map<*, *>): DeepseekClient {
        return DeepseekClient(
            apiKey = apiKey,
            model = model,
            thinking = MapUtil.toBoolean("thinking", config),
            maxTokens = MapUtil.toInt("max-tokens", config),
            temperature = MapUtil.toDouble("temperature", config),
            readTimeoutMillis = MapUtil.toLong("read-timeout-millis", config) ?: READ_TIMEOUT_MILLIS,
            connectTimeoutMillis = MapUtil.toLong("connect-timeout-millis", config) ?: CONNECT_TIMEOUT_MILLIS,
            jsonMapper = context.jsonMapper,
        )
    }
}
