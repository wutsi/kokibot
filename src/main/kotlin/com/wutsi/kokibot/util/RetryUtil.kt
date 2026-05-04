package com.wutsi.kokibot.util

import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Utility for retry logic with exponential backoff and jitter.
 *
 * Example usage:
 * ```
 * val result = RetryUtil.withRetry(
 *     maxAttempts = 3,
 *     initialDelayMs = 1000,
 *     maxDelayMs = 10000,
 *     retryOn = listOf(IOException::class, TimeoutException::class)
 * ) {
 *     // Your code that might fail
 *     callExternalAPI()
 * }
 * ```
 */
object RetryUtil {
    private val LOGGER = LoggerFactory.getLogger(RetryUtil::class.java)

    /**
     * Execute a block of code with retry logic.
     *
     * @param maxAttempts Maximum number of attempts (default: 3)
     * @param initialDelayMs Initial delay between retries in milliseconds (default: 1000)
     * @param maxDelayMs Maximum delay between retries in milliseconds (default: 30000)
     * @param backoffMultiplier Multiplier for exponential backoff (default: 2.0)
     * @param jitterFactor Random jitter to add (0.0 to 1.0, default: 0.1)
     * @param retryOn List of exception types to retry on (default: all exceptions)
     * @param onRetry Callback invoked before each retry attempt
     * @param block The code block to execute
     * @return Result of the block execution
     * @throws Exception if all retry attempts fail
     */
    fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 30000,
        backoffMultiplier: Double = 2.0,
        jitterFactor: Double = 0.1,
        retryOn: List<kotlin.reflect.KClass<out Throwable>> = listOf(Exception::class),
        onRetry: ((attempt: Int, exception: Throwable) -> Unit)? = null,
        block: () -> T,
    ): T {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }

        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (ex: Throwable) {
                // Check if this exception type should be retried
                val shouldRetry = retryOn.any { it.java.isInstance(ex) }

                if (!shouldRetry || attempt >= maxAttempts) {
                    LOGGER.warn("Operation failed after $attempt attempts", ex)
                    throw ex
                }

                lastException = ex

                // Calculate delay with exponential backoff and jitter
                val baseDelay = min(
                    initialDelayMs * backoffMultiplier.pow(attempt - 1).toLong(),
                    maxDelayMs
                )
                val jitter = (baseDelay * jitterFactor * Random.nextDouble()).toLong()
                val delayMs = baseDelay + jitter

                LOGGER.warn(
                    "Attempt $attempt/$maxAttempts failed with ${ex.javaClass.simpleName}: ${ex.message}. " +
                        "Retrying in ${delayMs}ms..."
                )

                // Invoke callback if provided
                onRetry?.invoke(attempt, ex)

                // Sleep before retry
                Thread.sleep(delayMs)
            }
        }

        // Should never reach here, but just in case
        throw lastException ?: IllegalStateException("Retry logic failed unexpectedly")
    }

    /**
     * Execute with fixed delay retry (no exponential backoff).
     */
    fun <T> withFixedRetry(
        maxAttempts: Int = 3,
        delayMs: Long = 1000,
        retryOn: List<kotlin.reflect.KClass<out Throwable>> = listOf(Exception::class),
        onRetry: ((attempt: Int, exception: Throwable) -> Unit)? = null,
        block: () -> T,
    ): T {
        return withRetry(
            maxAttempts = maxAttempts,
            initialDelayMs = delayMs,
            maxDelayMs = delayMs,
            backoffMultiplier = 1.0,
            jitterFactor = 0.0,
            retryOn = retryOn,
            onRetry = onRetry,
            block = block
        )
    }
}
