package com.wutsi.kokibot.llm.gemini

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.deepseek.DeepseekClient

/**
 * This is the implementation of the Deepseek LLM.
 * It uses the Deepseek API to generate responses.
 *
 * API Documentations:
 * - chat completion: https://platform.kimi.ai/docs/api/chat
 */
class Gemini : Deepseek() {
    override fun getName(): String {
        return "gemini"
    }

    override fun createClient(): DeepseekClient {
        return GeminiClient(
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

    override fun availableModels(): List<String> {
        return listOf(
            "gemini-3.5-flash",
            "gemini-3.1-flash-image",
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash-lite-image",
            "gemini-3.1-pro-preview",
            "gemini-3-pro-image",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
        )
    }
}
