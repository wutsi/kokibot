package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryUtilTest {

    @Test
    fun `withRetry succeeds on first attempt`() {
        val callCount = AtomicInteger(0)

        val result = RetryUtil.withRetry(maxAttempts = 3) {
            callCount.incrementAndGet()
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `withRetry succeeds after 2 failures`() {
        val callCount = AtomicInteger(0)

        val result = RetryUtil.withRetry(
            maxAttempts = 3,
            initialDelayMs = 10,
            retryOn = listOf(IOException::class)
        ) {
            val count = callCount.incrementAndGet()
            if (count < 3) {
                throw IOException("Temporary failure")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `withRetry throws after max attempts`() {
        val callCount = AtomicInteger(0)

        val ex = assertThrows<IOException> {
            RetryUtil.withRetry(
                maxAttempts = 3,
                initialDelayMs = 10,
                retryOn = listOf(IOException::class)
            ) {
                callCount.incrementAndGet()
                throw IOException("Persistent failure")
            }
        }

        assertEquals("Persistent failure", ex.message)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `withRetry does not retry on non-matching exception`() {
        val callCount = AtomicInteger(0)

        val ex = assertThrows<IllegalArgumentException> {
            RetryUtil.withRetry(
                maxAttempts = 3,
                initialDelayMs = 10,
                retryOn = listOf(IOException::class)
            ) {
                callCount.incrementAndGet()
                throw IllegalArgumentException("Should not retry")
            }
        }

        assertEquals("Should not retry", ex.message)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `withRetry retries on subclass exception`() {
        val callCount = AtomicInteger(0)

        val ex = assertThrows<SocketTimeoutException> {
            RetryUtil.withRetry(
                maxAttempts = 2,
                initialDelayMs = 10,
                retryOn = listOf(IOException::class) // SocketTimeoutException extends IOException
            ) {
                callCount.incrementAndGet()
                throw SocketTimeoutException("Timeout")
            }
        }

        assertEquals("Timeout", ex.message)
        assertEquals(2, callCount.get())
    }

    @Test
    fun `withRetry calls onRetry callback`() {
        val callCount = AtomicInteger(0)
        val retryAttempts = mutableListOf<Int>()
        val retryExceptions = mutableListOf<Throwable>()

        val result = RetryUtil.withRetry(
            maxAttempts = 3,
            initialDelayMs = 10,
            retryOn = listOf(IOException::class),
            onRetry = { attempt, exception ->
                retryAttempts.add(attempt)
                retryExceptions.add(exception)
            }
        ) {
            val count = callCount.incrementAndGet()
            if (count < 3) {
                throw IOException("Failure $count")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, callCount.get())
        assertEquals(listOf(1, 2), retryAttempts)
        assertEquals(2, retryExceptions.size)
        assertTrue(retryExceptions.all { it is IOException })
    }

    @Test
    fun `withRetry respects exponential backoff`() {
        val callCount = AtomicInteger(0)
        val start = System.currentTimeMillis()

        assertThrows<IOException> {
            RetryUtil.withRetry(
                maxAttempts = 3,
                initialDelayMs = 100,
                backoffMultiplier = 2.0,
                jitterFactor = 0.0, // No jitter for predictable timing
                retryOn = listOf(IOException::class)
            ) {
                callCount.incrementAndGet()
                throw IOException("Test")
            }
        }

        val elapsed = System.currentTimeMillis() - start

        // Should wait: 100ms + 200ms = 300ms (2 retries)
        assertTrue(elapsed >= 300, "Expected at least 300ms, got ${elapsed}ms")
        assertTrue(elapsed < 500, "Expected less than 500ms (with some margin), got ${elapsed}ms")
    }

    @Test
    fun `withFixedRetry uses fixed delay`() {
        val callCount = AtomicInteger(0)
        val start = System.currentTimeMillis()

        assertThrows<IOException> {
            RetryUtil.withFixedRetry(
                maxAttempts = 3,
                delayMs = 100,
                retryOn = listOf(IOException::class)
            ) {
                callCount.incrementAndGet()
                throw IOException("Test")
            }
        }

        val elapsed = System.currentTimeMillis() - start

        // Should wait: 100ms + 100ms = 200ms (2 retries)
        assertTrue(elapsed >= 200, "Expected at least 200ms, got ${elapsed}ms")
        assertTrue(elapsed < 350, "Expected less than 350ms, got ${elapsed}ms")
    }

    @Test
    fun `withRetry respects maxDelay cap`() {
        val callCount = AtomicInteger(0)

        assertThrows<IOException> {
            RetryUtil.withRetry(
                maxAttempts = 10,
                initialDelayMs = 100,
                maxDelayMs = 200,
                backoffMultiplier = 10.0,
                jitterFactor = 0.0,
                retryOn = listOf(IOException::class)
            ) {
                callCount.incrementAndGet()
                throw IOException("Test")
            }
        }

        // With exponential backoff of 10x, delay would be 100, 1000, 10000...
        // But maxDelay=200 should cap it at 200ms per retry
        assertEquals(10, callCount.get())
    }
}
