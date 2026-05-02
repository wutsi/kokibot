package com.wutsi.kokibot.llm.gemini

import com.wutsi.kokibot.llm.deepseek.DeepseekClient
import com.wutsi.kokibot.util.RestBuilder

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
) : DeepseekClient(
    apiKey = apiKey,
    model = model,
    thinking = thinking,
    temperature = temperature,
    maxTokens = maxTokens,
    readTimeoutMillis = readTimeoutMillis,
    connectTimeoutMillis = connectTimeoutMillis,
    restBuilder = restBuilder
) {
    override fun getBaseUrl(): String {
        return "https://generativelanguage.googleapis.com/v1beta/openai"
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") ||
            mimeType.startsWith("application/pdf")
    }
}
