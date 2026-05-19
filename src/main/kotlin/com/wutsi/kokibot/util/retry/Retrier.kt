package com.wutsi.kokibot.util.retry

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.client.HttpStatusCodeException
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlin.math.min
import kotlin.random.Random

/**
 * Executes an operation with exponential-backoff + jitter retry semantics.
 *
 * Use [execute] to run an idempotent operation. The caller may override
 * [shouldRetry] to enforce additional constraints (e.g. for streaming calls,
 * refuse to retry once data has been forwarded to the consumer).
 */
class Retrier(
    private val policy: RetryPolicy,
    private val random: Random = Random.Default,
    private val logger: Logger = LoggerFactory.getLogger(Retrier::class.java),
) {
    fun <T> execute(
        operationName: String = "operation",
        shouldRetry: (Throwable) -> Boolean = RetryClassifier::isRetryable,
        op: (attempt: Int) -> T,
    ): T {
        val start = System.currentTimeMillis()
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < policy.maxAttempts) {
            attempt++
            try {
                return op(attempt)
            } catch (ex: Throwable) {
                lastError = ex

                if (!shouldRetry(ex)) {
                    throw ex
                }
                if (attempt >= policy.maxAttempts) {
                    logger.error(
                        "[{}] giving up after {} attempt(s), elapsed={}ms: {}",
                        operationName, attempt, System.currentTimeMillis() - start, ex.toString()
                    )
                    throw ex
                }

                val backoff = computeBackoffMillis(attempt)
                val retryAfter = if (policy.respectRetryAfter) retryAfterMillis(ex) else null
                val delay = maxOf(backoff, retryAfter ?: 0)

                val elapsed = System.currentTimeMillis() - start
                val budget = policy.maxTotalElapsedMillis
                if (budget != null && elapsed + delay > budget) {
                    logger.error(
                        "[{}] retry budget exhausted (elapsed={}ms, next delay={}ms, budget={}ms): {}",
                        operationName, elapsed, delay, budget, ex.toString()
                    )
                    throw ex
                }

                logger.warn(
                    "[{}] attempt {}/{} failed, retrying in {}ms ({}{}): {}",
                    operationName,
                    attempt,
                    policy.maxAttempts,
                    delay,
                    if (retryAfter != null) "Retry-After=${retryAfter}ms, " else "",
                    "backoff=${backoff}ms",
                    ex.toString()
                )
                sleep(delay)
            }
        }
        // Unreachable: loop either returns or throws.
        throw lastError ?: IllegalStateException("Retrier exited without result")
    }

    private fun sleep(millis: Long) {
        if (millis > 0) {
            Thread.sleep(millis)
        }
    }

    private fun computeBackoffMillis(attempt: Int): Long {
        // attempt is 1-based; backoff applies AFTER attempt N, so use exponent (attempt - 1).
        val exp = Math.pow(policy.backoffMultiplier, (attempt - 1).toDouble())
        val raw = (policy.initialBackoffMillis * exp).toLong()
        val capped = min(raw, policy.maxBackoffMillis)
        if (policy.jitterFactor <= 0.0 || capped <= 0) {
            return capped
        }
        val jitterRange = (capped * policy.jitterFactor).toLong().coerceAtLeast(1)
        val delta = random.nextLong(-jitterRange, jitterRange + 1)
        return (capped + delta).coerceAtLeast(0)
    }

    private fun retryAfterMillis(ex: Throwable): Long? {
        if (ex !is HttpStatusCodeException) return null
        val headerValue = ex.responseHeaders?.getFirst(HttpHeaders.RETRY_AFTER) ?: return null
        val trimmed = headerValue.trim()

        // RFC 7231: either delta-seconds or HTTP-date.
        trimmed.toLongOrNull()?.let { seconds ->
            return (seconds * 1000).coerceAtLeast(0)
        }
        return try {
            val target = java.time.ZonedDateTime.parse(
                trimmed,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            )
            val now = java.time.ZonedDateTime.now(target.zone)
            Duration.between(now, target).toMillis().coerceAtLeast(0)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
