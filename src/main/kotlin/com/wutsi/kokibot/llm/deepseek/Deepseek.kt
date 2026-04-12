package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.util.MapUtil

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 */
class Deepseek : LLM {
    companion object {
        val CONNECT_TIMEOUT_MILLIS = 5000L
        val READ_TIMEOUT_MILLIS = 60000L
    }

    internal lateinit var client: DeepseekClient

    /**
     * Initialize the Deepseek client with the given configuration and context.
     * The configuration can contain the following parameters:
     * - api_key: the API key for Deepseek (required)
     * - model: the model to use for generation (required)
     * - thinking: whether to enable thinking mode
     * - max-tokens: the maximum number of tokens to generate
     * - temperature: the temperature to use for generation
     * - read-timeout-millis: the read timeout in milliseconds (default: 60000)
     * - connect-timeout-millis: the connect timeout in milliseconds (default: 5000)
     */
    override fun init(config: Map<*, *>, context: Context) {
        val apiKey = config["api_key"] as String? ?: throw ConfigurationException("api_key is required")
        val model = config["model"] as String? ?: throw ConfigurationException("model is required")

        client = DeepseekClient(
            apiKey = apiKey,
            model = model,
            thinking = MapUtil.toBoolean("thinking", config),
            maxTokens = MapUtil.toInt("max-tokens", config),
            temperature = MapUtil.toDouble("temperature", config),
            readTimeoutMillis = MapUtil.toLong("read-timeout-millis", config) ?: READ_TIMEOUT_MILLIS,
            connectTimeoutMillis = MapUtil.toLong("connect-timeout-millis", config) ?: CONNECT_TIMEOUT_MILLIS,
        )
    }

    override fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        return client.completion(request, tools)
    }
}
