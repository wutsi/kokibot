package com.wutsi.kokibot.llm.deepseek

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.util.RestBuilder
import com.wutsi.kokibot.util.retry.RetryPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.io.IOException

class DeepseekClientRetryTest {
    companion object {
        const val API_KEY = "sd-xxxxxxx"
        const val MODEL = "deepseek-v4-flash"
        const val URL = "https://api.deepseek.com/chat/completions"
    }

    private val rest = mock<RestTemplate>()
    private val restBuilder = mock<RestBuilder>()

    private val okBody = mapOf(
        "id" to "ds-1",
        "model" to MODEL,
        "choices" to listOf(
            mapOf(
                "finish_reason" to "stop",
                "index" to 0,
                "message" to mapOf("content" to "ok", "role" to "assistant")
            )
        )
    )

    @BeforeEach
    fun setUp() {
        doReturn(rest).whenever(restBuilder).build(anyOrNull(), anyOrNull())
    }

    @Test
    fun `retries on 5xx then succeeds`() {
        val responses = ArrayDeque<Any>(
            listOf(
                HttpServerErrorException(HttpStatus.BAD_GATEWAY),
                HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE),
                ResponseEntity(okBody, HttpStatus.OK)
            )
        )
        Mockito.`when`(
            rest.postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
        ).thenAnswer(Answer<Any> { _: InvocationOnMock ->
            when (val next = responses.removeFirst()) {
                is Throwable -> throw next
                else -> next
            }
        })

        val client = createClient(RetryPolicy(maxAttempts = 3, initialBackoffMillis = 1, jitterFactor = 0.0))
        val resp = client.completion(LLMRequest(prompt = "hi"), emptyList())

        assertEquals("ds-1", resp.id)
        verify(rest, Mockito.times(3))
            .postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
    }

    @Test
    fun `does not retry on 4xx`() {
        whenever(rest.postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java)))
            .thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))

        val client = createClient(RetryPolicy(maxAttempts = 5, initialBackoffMillis = 1, jitterFactor = 0.0))
        assertThrows(HttpClientErrorException::class.java) {
            client.completion(LLMRequest(prompt = "hi"), emptyList())
        }
        verify(rest, Mockito.times(1))
            .postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
    }

    @Test
    fun `retries on network IO errors`() {
        val responses = ArrayDeque<Any>(
            listOf(
                ResourceAccessException("timeout", IOException("read timed out")),
                ResponseEntity(okBody, HttpStatus.OK)
            )
        )
        Mockito.`when`(
            rest.postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
        ).thenAnswer(Answer<Any> { _: InvocationOnMock ->
            when (val next = responses.removeFirst()) {
                is Throwable -> throw next
                else -> next
            }
        })

        val client = createClient(RetryPolicy(maxAttempts = 3, initialBackoffMillis = 1, jitterFactor = 0.0))
        val resp = client.completion(LLMRequest(prompt = "hi"), emptyList())

        assertEquals("ds-1", resp.id)
        verify(rest, Mockito.times(2))
            .postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
    }

    @Test
    fun `honors Retry-After on 429`() {
        val headers = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "1") }
        val responses = ArrayDeque<Any>(
            listOf(
                HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "rate limit", headers, ByteArray(0), null
                ),
                ResponseEntity(okBody, HttpStatus.OK)
            )
        )
        Mockito.`when`(
            rest.postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
        ).thenAnswer(Answer<Any> { _: InvocationOnMock ->
            when (val next = responses.removeFirst()) {
                is Throwable -> throw next
                else -> next
            }
        })

        val client = createClient(RetryPolicy(maxAttempts = 3, initialBackoffMillis = 1, jitterFactor = 0.0))
        val start = System.currentTimeMillis()
        client.completion(LLMRequest(prompt = "hi"), emptyList())
        val elapsed = System.currentTimeMillis() - start

        // Should have slept ~1s due to Retry-After (not the 1ms backoff).
        assertTrue(elapsed >= 900, "expected >= 900ms elapsed, got $elapsed")
    }

    @Test
    fun `exhausts attempts on persistent 503`() {
        doThrow(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
            .whenever(rest)
            .postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))

        val client = createClient(RetryPolicy(maxAttempts = 3, initialBackoffMillis = 1, jitterFactor = 0.0))
        assertThrows(HttpServerErrorException::class.java) {
            client.completion(LLMRequest(prompt = "hi"), emptyList())
        }
        verify(rest, Mockito.times(3))
            .postForEntity(eq(URL), any<HttpEntity<*>>(), eq(Map::class.java))
    }

    private fun createClient(policy: RetryPolicy): DeepseekClient =
        DeepseekClient(
            apiKey = API_KEY,
            model = MODEL,
            restBuilder = restBuilder,
            retryPolicy = policy,
        )
}
