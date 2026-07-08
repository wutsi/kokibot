package com.wutsi.kokibot.llm.kimi

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.deepseek.DeepseekClient

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 *
 * API Documentations:
 * - chat completion: https://platform.kimi.ai/docs/api/chat
 */
class Kimi : Deepseek() {
    override fun getName(): String {
        return "kimi"
    }

    override fun createClient(): DeepseekClient {
        return KimiClient(
            apiKey = getApiKey(),
            model = model,
            thinking = thinking,
            maxTokens = maxTokens,
            temperature = temperature,
            readTimeoutMillis = readTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            jsonMapper = context.jsonMapper,
            responseFormat = responseFormat,
        )
    }

    override fun getMaxContextWindow(): Int {
        return when {
            model.startsWith("kimi-k2.") -> 256 * 1024
            model.startsWith("moonshot-v1-128k") -> 128 * 1024
            model.startsWith("moonshot-v1-32k") -> 32 * 1024
            model.startsWith("moonshot-v1-8k") -> 8 * 1024
            else -> throw IllegalArgumentException("Unsupported model: $model")
        }
    }

    override fun availableModels(): List<String> {
        return listOf(
            "kimi-k2.6",
            "kimi-k2.7-code",
            "kimi-k2.7-code-highspeed",
            "kimi-k2.5",
            "moonshot-v1-128k",
            "moonshot-v1-32k",
            "moonshot-v1-8k",
            "moonshot-v1-128k-vision-preview",
            "moonshot-v1-32k-vision-preview",
            "moonshot-v1-8k-vision-preview"
        )
    }
}
