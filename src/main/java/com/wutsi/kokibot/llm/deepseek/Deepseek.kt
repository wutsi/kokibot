package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.util.MapUtil

class Deepseek : LLM {
    internal lateinit var client: DeepseekClient

    override fun init(config: Map<*, *>, context: Context) {
        val apiKey = config["api_key"] as String? ?: throw ConfigurationException("api_key is required")
        val model = config["model"] as String? ?: throw ConfigurationException("model is required")

        client = DeepseekClient(
            apiKey = apiKey,
            model = model,
            thinking = MapUtil.toBoolean("thinking", config),
            maxTokens = MapUtil.toInt("max-tokens", config),
            temperature = MapUtil.toDouble("temperature", config),
            readTimeoutMillis = MapUtil.toLong("read-timeout-millis", config),
            connectTimeoutMillis = MapUtil.toLong("connect-timeout-millis", config),
            toolRegistry = context.toolRegistry,
        )
    }

    override fun completion(request: LLMRequest): LLMResponse {
        return client.completion(request)
    }
}
