package com.wutsi.kokibot.util

import kotlin.reflect.KClass

/**
 * Configuration for retry strategies.
 * Provides pre-configured retry policies for common scenarios.
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val retryOn: List<KClass<out Throwable>> = listOf(Exception::class),
) {
    companion object {
        /**
         * Default retry config - 3 attempts, exponential backoff starting at 1s
         */
        fun default() = RetryConfig()

        /**
         * Fast retry - for quick operations that might have transient failures
         * 3 attempts, 500ms initial delay, max 2s
         */
        fun fast() = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 500,
            maxDelayMs = 2000,
            backoffMultiplier = 2.0,
            jitterFactor = 0.1
        )

        /**
         * Network retry - for HTTP/network operations
         * Retries on network-related exceptions only
         */
        fun network() = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 1000,
            maxDelayMs = 10000,
            backoffMultiplier = 2.0,
            jitterFactor = 0.2,
            retryOn = listOf(
                java.net.SocketTimeoutException::class,
                java.net.ConnectException::class,
                java.io.IOException::class,
                org.springframework.web.client.HttpServerErrorException::class,
                org.springframework.web.client.ResourceAccessException::class
            )
        )

        /**
         * LLM API retry - for LLM provider calls
         * More generous delays to avoid rate limits, retries on transient errors
         */
        fun llm() = RetryConfig(
            maxAttempts = 4,
            initialDelayMs = 2000,
            maxDelayMs = 30000,
            backoffMultiplier = 3.0,
            jitterFactor = 0.2,
            retryOn = listOf(
                java.net.SocketTimeoutException::class,
                java.io.IOException::class,
                org.springframework.web.client.HttpServerErrorException::class,
                org.springframework.web.client.ResourceAccessException::class
            )
        )

        /**
         * Database/file retry - for I/O operations
         * Retries on I/O exceptions, shorter delays
         */
        fun io() = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            maxDelayMs = 1000,
            backoffMultiplier = 2.0,
            jitterFactor = 0.1,
            retryOn = listOf(
                java.io.IOException::class,
                java.nio.file.FileSystemException::class
            )
        )

        /**
         * Aggressive retry - for critical operations
         * Many attempts with longer delays
         */
        fun aggressive() = RetryConfig(
            maxAttempts = 10,
            initialDelayMs = 5000,
            maxDelayMs = 60000,
            backoffMultiplier = 1.5,
            jitterFactor = 0.3
        )

        /**
         * No retry - execute once only (useful for testing or disabling retries)
         */
        fun none() = RetryConfig(
            maxAttempts = 1,
            initialDelayMs = 0,
            maxDelayMs = 0,
            backoffMultiplier = 1.0,
            jitterFactor = 0.0
        )
    }

    /**
     * Execute a block of code with this retry configuration.
     */
    fun <T> execute(
        onRetry: ((attempt: Int, exception: Throwable) -> Unit)? = null,
        block: () -> T,
    ): T {
        return RetryUtil.withRetry(
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            backoffMultiplier = backoffMultiplier,
            jitterFactor = jitterFactor,
            retryOn = retryOn,
            onRetry = onRetry,
            block = block
        )
    }
}
