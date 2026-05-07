package com.wutsi.kokibot.llm.kimi

import com.wutsi.kokibot.llm.deepseek.DeepseekClient
import com.wutsi.kokibot.util.RestBuilder
import tools.jackson.databind.json.JsonMapper

/**
 * Implementation of the Kimi LLM client.
 * It extends the DeepseekClient since they are both based on OpenAI API.
 * Refer to the Kimi documentation for more details: https://platform.kimi.ai/docs/api/overview
 */
class KimiClient(
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
        return "https://api.moonshot.ai/v1"
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }
}
