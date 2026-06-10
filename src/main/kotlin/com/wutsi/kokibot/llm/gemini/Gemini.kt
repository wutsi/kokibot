package com.wutsi.kokibot.llm.gemini

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.deepseek.DeepseekClient
import com.wutsi.kokibot.util.MapUtil

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 *
 * API Documentations:
 * - chat completion: https://platform.kimi.ai/docs/api/chat
 */
class Gemini : Deepseek() {
    override fun name(): String {
        return "gemini"
    }

    override fun createClient(apiKey: String, model: String, config: Map<*, *>): DeepseekClient {
        return GeminiClient(
            apiKey = apiKey,
            model = model,
            jsonMapper = context.jsonMapper,
            thinking = MapUtil.toBoolean("thinking", config),
            maxTokens = MapUtil.toInt("max-tokens", config),
            temperature = MapUtil.toDouble("temperature", config),
            readTimeoutMillis = MapUtil.toLong("read-timeout-millis", config) ?: READ_TIMEOUT_MILLIS,
            connectTimeoutMillis = MapUtil.toLong("connect-timeout-millis", config) ?: CONNECT_TIMEOUT_MILLIS,
        )
    }
}
