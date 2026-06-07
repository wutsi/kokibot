package com.wutsi.kokibot.llm.gemini

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class GeminiClientTest {
    @Test
    fun `constructor - with minimal parameters`() {
        GeminiClient(
            apiKey = "test-key",
            model = "gemini-2.5-flash-lite",
            jsonMapper = JsonMapper()
        )
    }

    @Test
    fun `constructor - with all parameters`() {
        GeminiClient(
            apiKey = "test-key",
            model = "gemini-2.5-flash-lite",
            thinking = true,
            temperature = 0.7,
            maxTokens = 2000,
            readTimeoutMillis = 60000,
            connectTimeoutMillis = 30000,
            jsonMapper = JsonMapper()
        )
    }
}
