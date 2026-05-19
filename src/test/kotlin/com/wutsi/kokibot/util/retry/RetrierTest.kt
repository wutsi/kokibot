package com.wutsi.kokibot.util.retry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.random.Random

class RetrierTest {
    private fun retrier(policy: RetryPolicy = RetryPolicy(jitterFactor = 0.0)) =
        Retrier(policy = policy, random = Random(0))

    @Test
    fun `returns immediately on success`() {
        var attempts = 0
        val result = retrier().execute { attempt ->
            attempts = attempt
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retries on retryable error then succeeds`() {
        var attempts = 0
        val result = retrier(RetryPolicy(maxAttempts = 3, initialBackoffMillis = 1, jitterFactor = 0.0))
            .execute { attempt ->
                attempts = attempt
                if (attempt < 3) throw ResourceAccessException("boom") else "ok"
            }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `gives up after maxAttempts`() {
        var attempts = 0
        val ex = assertThrows(HttpServerErrorException::class.java) {
            retrier(RetryPolicy(maxAttempts = 2, initialBackoffMillis = 1, jitterFactor = 0.0))
                .execute { attempt ->
                    attempts = attempt
                    throw HttpServerErrorException(HttpStatus.BAD_GATEWAY)
                }
        }
        assertEquals(502, ex.statusCode.value())
        assertEquals(2, attempts)
    }

    @Test
    fun `does not retry non-retryable error`() {
        val original = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        var attempts = 0
        val ex = assertThrows(HttpClientErrorException::class.java) {
            retrier().execute { attempt ->
                attempts = attempt
                throw original
            }
        }
        assertSame(original, ex)
        assertEquals(1, attempts)
    }

    @Test
    fun `honors Retry-After header in seconds on 429`() {
        val headers = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "1") }
        var attempt = 0
        val start = System.currentTimeMillis()
        val result = retrier(RetryPolicy(maxAttempts = 2, initialBackoffMillis = 1, jitterFactor = 0.0))
            .execute {
                attempt++
                if (attempt == 1) {
                    throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "rate", headers, ByteArray(0), null
                    )
                }
                "ok"
            }
        val elapsed = System.currentTimeMillis() - start
        assertEquals("ok", result)
        // Should have slept ~1s due to Retry-After (not the 1ms backoff).
        assertTrue(elapsed >= 900, "expected >= 900ms elapsed, got $elapsed")
    }

    @Test
    fun `shouldRetry override is respected`() {
        var attempts = 0
        val ex = assertThrows(HttpServerErrorException::class.java) {
            retrier(RetryPolicy(maxAttempts = 5, initialBackoffMillis = 1, jitterFactor = 0.0))
                .execute(shouldRetry = { false }) { attempt ->
                    attempts = attempt
                    throw HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
                }
        }
        assertEquals(503, ex.statusCode.value())
        assertEquals(1, attempts)
    }

    @Test
    fun `stops retrying when next delay would exceed total budget`() {
        // Budget of 100ms, but the first backoff would already be 5s -> abort on first failure.
        var attempts = 0
        assertThrows(HttpServerErrorException::class.java) {
            Retrier(
                policy = RetryPolicy(
                    maxAttempts = 10,
                    initialBackoffMillis = 5_000,
                    maxBackoffMillis = 5_000,
                    jitterFactor = 0.0,
                    maxTotalElapsedMillis = 100,
                ),
                random = Random(0),
            ).execute { attempt ->
                attempts = attempt
                throw HttpServerErrorException(HttpStatus.BAD_GATEWAY)
            }
        }
        assertEquals(1, attempts)
    }
}
