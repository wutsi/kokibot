package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMBalance
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 */
open class Deepseek : LLM {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Deepseek::class.java)
        const val CONNECT_TIMEOUT_MILLIS = 5000L
        const val READ_TIMEOUT_MILLIS = 60000L
    }

    protected lateinit var context: Context
    internal lateinit var model: String
    internal var streamingEnabled: Boolean = false
    internal var thinking: Boolean = false
    internal var reasoningEffort: String? = null
    internal var readTimeoutMillis: Long = READ_TIMEOUT_MILLIS
    internal var connectTimeoutMillis: Long? = CONNECT_TIMEOUT_MILLIS
    internal var temperature: Double? = null
    internal var maxTokens: Int? = null
    internal var responseFormat: String? = null

    override fun getName(): String {
        return "deepseek"
    }

    override fun getModel(): String {
        return model
    }

    override fun getReasoningEffort(): String? {
        return reasoningEffort
    }

    override fun getTemperature(): Double? {
        return temperature
    }

    override fun getResponseFormat(): String? {
        return responseFormat
    }

    /**
     * Initialize the Deepseek client with the given configuration and context.
     * The configuration can contain the following parameters:
     * - The API key is resolved via `context.credentialService.get("llm.{name()}")`
     * - model: the model to use for generation. Values: deepseek-chat, deepseek-reasoner (required)
     * - thinking: `true` to enable thinking mode, `false` to disable (default: false)
     * - max-tokens: the maximum number of tokens to generate
     * - temperature: the temperature to use for generation
     * - read-timeout-millis: the read timeout in milliseconds (default: 60000)
     * - connect-timeout-millis: the connect timeout in milliseconds (default: 5000)
     */
    override fun init(config: Map<*, *>, context: Context) {
        model = config["model"] as String? ?: throw ConfigurationException("model is required")

        this.context = context
        this.streamingEnabled = MapUtil.toBoolean("streaming", config) ?: false
        this.thinking = MapUtil.toBoolean("thinking", config) ?: false
        this.reasoningEffort = MapUtil.toString("reasoning-effort", config)
        this.maxTokens = MapUtil.toInt("max-tokens", config)
        this.temperature = MapUtil.toDouble("temperature", config)
        this.readTimeoutMillis = MapUtil.toLong("read-timeout-millis", config) ?: READ_TIMEOUT_MILLIS
        this.connectTimeoutMillis = MapUtil.toLong("connect-timeout-millis", config) ?: CONNECT_TIMEOUT_MILLIS
        this.responseFormat = config["response-format"] as String?

        LOGGER.info("LLM: " + config["type"])
        LOGGER.info(" model: $model")
        LOGGER.info(" streaming: ${this.streamingEnabled}")
        LOGGER.info(" thinking: ${this.thinking}")
        if (responseFormat != null) {
            LOGGER.info(" response-format: ${this.responseFormat}")
        }
        if (this.reasoningEffort != null) {
            LOGGER.info(" reasoning-effort: ${this.reasoningEffort}")
        }
        if (this.temperature != null) {
            LOGGER.info(" temperature: ${this.temperature}")
        }
    }

    override fun health(): Health {
        return try {
            createClient().completion(LLMRequest(prompt = "Hello"), emptyList())
            Health(id = id())
        } catch (ex: Exception) {
            Health(id = id(), up = false, details = ex.message ?: "unknown error")
        }
    }

    override fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        return createClient().completion(request, tools)
    }

    override fun supportsStreaming(): Boolean {
        return streamingEnabled
    }

    override fun completionStream(
        request: LLMRequest,
        tools: List<Tool>,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse {
        return createClient().completionStream(request, tools, onChunk)
    }

    override fun balance(): LLMBalance? {
        return createClient().balance()
    }

    override fun availableModels(): List<String> {
        return listOf(
            "deepseek-v4-flash",
            "deepseek-v4-pro",
        )
    }

    protected open fun createClient(): DeepseekClient {
        return DeepseekClient(
            apiKey = getApiKey(),
            model = model,
            jsonMapper = context.jsonMapper,
            thinking = thinking,
            reasoningEffort = reasoningEffort,
            maxTokens = maxTokens,
            temperature = temperature,
            readTimeoutMillis = readTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            responseFormat = responseFormat,
        )
    }

    protected fun getApiKey(): String {
        val key = "llm.${getName()}"
        val apiKey = context.credentialService.get(key)
        return apiKey
    }

    override fun getMaxContextWindow() = 1024 * 1024

    override fun apply(name: String, value: Any) {
        when (name) {
            "reasoning-effort" -> reasoningEffort = value.toString()
            "temperature" -> temperature = value.toString().toDoubleOrNull()
            "response-format" -> responseFormat = value.toString()
            else -> throw ConfigurationException("Unknown setting: $name")
        }
    }
}
