package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.tools.ToolRegistry
import com.wutsi.kokibot.util.RestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals

class DeepseekClientTest {
    companion object {
        const val API_KEY = "sd-xxxxxxx"
        const val MODEL = "deepseek-chat"
    }

    private val rest = mock<RestTemplate>()
    private val restBuilder = mock<RestBuilder>()
    private val toolRegistry = mock<ToolRegistry>()

    private val dsContentResponse = mapOf(
        "id" to "ds-id-000000000",
        "usage" to mapOf(
            "prompt_tokens" to 10,
            "completion_tokens" to 20,
            "total_tokens" to 30
        ),
        "choices" to listOf(
            mapOf(
                "finish_reason" to "stop",
                "index" to 0,
                "message" to mapOf(
                    "content" to "Hello, how can I help you?",
                    "role" to "assistant",
                    "reasoning_content" to "Thinking..."
                )
            )
        )
    )
    private val dsToolCallResponse = mapOf(
        "id" to "ds-id-000000000",
        "usage" to mapOf(
            "prompt_tokens" to 10,
            "completion_tokens" to 20,
            "total_tokens" to 30
        ),
        "choices" to listOf(
            mapOf(
                "finish_reason" to "tool_calls",
                "index" to 0,
                "message" to mapOf(
                    "content" to "calling a tool to get time",
                    "role" to "assistant",
                    "tool_calls" to listOf(
                        mapOf(
                            "id" to "call-0000001",
                            "type" to "function",
                            "function" to mapOf(
                                "name" to "date_tool_now",
                                "arguments" to "{\"location\": \"Paris\"}"
                            ),
                        )
                    )
                )
            )
        )
    )

    @BeforeEach
    fun setUp() {
        doReturn(rest).whenever(restBuilder).build(anyOrNull(), anyOrNull())

        doReturn(ResponseEntity(dsContentResponse, HttpStatus.OK))
            .whenever(rest)
            .postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), any<HttpEntity<*>>(), eq(Map::class.java))
    }

    @Test
    fun completion() {
        // WHEN
        val request = LLMRequest(
            prompt = "Hi sir",
            systemInstructions = "You are a helpful assistant"
        )
        val client = createClient()
        val response = client.completion(request)

        // THEN
        assertEquals("ds-id-000000000", response.id)
        assertEquals(1, response.choices.size)
        assertEquals(0, response.choices[0].index)
        assertEquals(LLMFinishReason.STOP, response.choices[0].finishReason)
        assertEquals("Hello, how can I help you?", response.choices[0].content)
        assertEquals("Thinking...", response.choices[0].reasoningContent)
        assertEquals(0, response.choices[0].toolCalls.size)

        val req = argumentCaptor<HttpEntity<Map<*, *>>>()
        verify(rest).postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), req.capture(), eq(Map::class.java))

        assertEquals("Bearer $API_KEY", req.firstValue.headers["Authorization"]?.firstOrNull())

        val body = req.firstValue.body as Map<*, *>
        assertEquals(6, body.size)
        assertEquals(MODEL, body["model"])
        assertEquals("enabled", body["thinking"])
        assertEquals(2048, body["max_tokens"])
        assertEquals(1.0, body["temperature"])
        assertEquals(true, body["parallel_tool_calls"])
        assertEquals(
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to "Hi sir"
                ),
                mapOf(
                    "role" to "system",
                    "content" to "You are a helpful assistant"
                )
            ),
            body["messages"]
        )
    }

    @Test
    fun `completion with default configuration`() {
        // WHEN
        val request = LLMRequest(
            prompt = "Hi sir",
            systemInstructions = "You are a helpful assistant"
        )
        val client = createDefaultClient()
        val response = client.completion(request)

        // THEN
        assertEquals("ds-id-000000000", response.id)
        assertEquals(1, response.choices.size)
        assertEquals(0, response.choices[0].index)
        assertEquals(LLMFinishReason.STOP, response.choices[0].finishReason)
        assertEquals("Hello, how can I help you?", response.choices[0].content)
        assertEquals("Thinking...", response.choices[0].reasoningContent)

        val req = argumentCaptor<HttpEntity<Map<*, *>>>()
        verify(rest).postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), req.capture(), eq(Map::class.java))

        assertEquals("Bearer $API_KEY", req.firstValue.headers["Authorization"]?.firstOrNull())

        val body = req.firstValue.body as Map<*, *>
        assertEquals(3, body.size)
        assertEquals(MODEL, body["model"])
        assertEquals(true, body["parallel_tool_calls"])
        assertEquals(
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to "Hi sir"
                ),
                mapOf(
                    "role" to "system",
                    "content" to "You are a helpful assistant"
                )
            ),
            body["messages"]
        )
    }

    @Test
    fun `completion without system instructions`() {
        // GIVEN
        doReturn(ResponseEntity(dsContentResponse, HttpStatus.OK))
            .whenever(rest)
            .postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), any<HttpEntity<*>>(), eq(Map::class.java))

        // WHEN
        val request = LLMRequest(
            prompt = "Hi sir",
            systemInstructions = null
        )
        val client = createClient()
        val response = client.completion(request)

        // THEN
        assertEquals("ds-id-000000000", response.id)
        assertEquals(1, response.choices.size)
        assertEquals(0, response.choices[0].index)
        assertEquals(LLMFinishReason.STOP, response.choices[0].finishReason)
        assertEquals("Hello, how can I help you?", response.choices[0].content)
        assertEquals("Thinking...", response.choices[0].reasoningContent)

        val req = argumentCaptor<HttpEntity<Map<*, *>>>()
        verify(rest).postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), req.capture(), eq(Map::class.java))
        val body = req.firstValue.body as Map<*, *>
        assertEquals(
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to "Hi sir"
                ),
            ),
            body["messages"]
        )
    }

    @Test
    fun `completion with function call`() {
        // GIVEN
        doReturn(ResponseEntity(dsToolCallResponse, HttpStatus.OK))
            .whenever(rest)
            .postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), any<HttpEntity<*>>(), eq(Map::class.java))

        // WHEN
        val request = LLMRequest(prompt = "Hi sir")
        val client = createClient()
        val response = client.completion(request)

        // THEN
        assertEquals("ds-id-000000000", response.id)
        assertEquals(1, response.choices.size)
        assertEquals(0, response.choices[0].index)
        assertEquals(LLMFinishReason.TOOL_CALLS, response.choices[0].finishReason)
        assertEquals("calling a tool to get time", response.choices[0].content)
        assertEquals(null, response.choices[0].reasoningContent)

        assertEquals(1, response.choices[0].toolCalls.size)
        assertEquals("date_tool_now", response.choices[0].toolCalls[0].name)
        assertEquals(mapOf("location" to "Paris"), response.choices[0].toolCalls[0].arguments)

        val req = argumentCaptor<HttpEntity<Map<*, *>>>()
        verify(rest).postForEntity(eq(DeepseekClient.COMPLETION_ENDPOINT), req.capture(), eq(Map::class.java))
        val body = req.firstValue.body as Map<*, *>
        assertEquals(
            listOf(
                mapOf(
                    "role" to "user",
                    "content" to "Hi sir"
                ),
            ),
            body["messages"]
        )
    }

    private fun createClient(): DeepseekClient {
        return DeepseekClient(
            apiKey = API_KEY,
            model = MODEL,
            thinking = true,
            temperature = 1.0,
            maxTokens = 2048,
            readTimeoutMillis = 1000,
            connectTimeoutMillis = 3000,
            restBuilder = restBuilder,
            toolRegistry = toolRegistry,
        )
    }

    private fun createDefaultClient(): DeepseekClient {
        return DeepseekClient(
            apiKey = API_KEY,
            model = MODEL,
            restBuilder = restBuilder,
            toolRegistry = toolRegistry,
        )
    }
}
