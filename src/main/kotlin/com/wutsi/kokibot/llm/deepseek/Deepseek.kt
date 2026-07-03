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

    protected lateinit var context: Context
    internal lateinit var model: String
    internal var streamingEnabled: Boolean = false
    internal var thinking: Boolean = false
    internal var reasoningEffort: String? = null
    internal var readTimeoutMillis: Long = READ_TIMEOUT_MILLIS
    internal var connectTimeoutMillis: Long? = CONNECT_TIMEOUT_MILLIS
    internal var temperature: Double? = null
    internal var maxTokens: Int? = null

    override fun getName(): String {
        return "deepseek"
    }

    override fun getModel(): String {
        return model
    }

    override fun getReasoningEffort(): String? {
        return reasoningEffort
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

        LOGGER.info("LLM: " + config["type"])
        LOGGER.info(" model: $model")
        LOGGER.info(" streaming: ${this.streamingEnabled}")
        LOGGER.info(" thinking: ${this.thinking}")
        if (this.reasoningEffort != null) {
            LOGGER.info(" reasoning_effort: ${this.reasoningEffort}")
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
        )
    }

    protected fun getApiKey(): String {
        return context.credentialService.get("llm.${getName()}")
    }

    override fun getMaxContextWindow() = 1024 * 1024
}
