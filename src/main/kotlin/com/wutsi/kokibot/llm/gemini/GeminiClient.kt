package com.wutsi.kokibot.llm.gemini

import com.wutsi.kokibot.llm.deepseek.DeepseekClient
import com.wutsi.kokibot.util.RestBuilder
import tools.jackson.databind.json.JsonMapper

/**
 * Implementation of the Gemini LLM client.
 * It extends the DeepseekClient since they are both based on OpenAI API.
 */
class GeminiClient(
    apiKey: String,
    model: String,
    thinking: Boolean? = null,
    temperature: Double? = null,
    maxTokens: Int? = null,
    readTimeoutMillis: Long? = null,
    connectTimeoutMillis: Long? = null,
    restBuilder: RestBuilder = RestBuilder(),
    jsonMapper: JsonMapper = JsonMapper(),
) : DeepseekClient(
    apiKey = apiKey,
    model = model,
    thinking = thinking,
    temperature = temperature,
    maxTokens = maxTokens,
    readTimeoutMillis = readTimeoutMillis,
    connectTimeoutMillis = connectTimeoutMillis,
    restBuilder = restBuilder,
    jsonMapper = jsonMapper,
) {
    override fun getBaseUrl(): String {
        return "https://generativelanguage.googleapis.com/v1beta/openai"
    }

    override fun getBalanceUrl(): String? {
        return null
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") ||
            mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") ||
            mimeType.startsWith("application/pdf")
    }
}
