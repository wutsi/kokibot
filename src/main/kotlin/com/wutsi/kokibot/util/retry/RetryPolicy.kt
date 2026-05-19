package com.wutsi.kokibot.util.retry

/**
 * Configuration for the [Retrier].
 *
 * - [maxAttempts]: total attempts including the first call. `1` disables retry.
 * - [initialBackoffMillis] / [backoffMultiplier] / [maxBackoffMillis]: exponential backoff schedule.
 * - [jitterFactor]: random jitter applied to each computed delay (`0.0` = no jitter, `0.2` = ±20%).
 * - [maxTotalElapsedMillis]: hard ceiling on cumulative time spent retrying. `null` = unlimited.
 * - [respectRetryAfter]: when `true`, honor the HTTP `Retry-After` header on 429/503 responses.
 *
 * Note on token billing: each retry of a `completion()` may be re-billed by the
 * provider if the server actually processed the request before failing (rare for
 * 5xx/timeouts, possible for 504). Tune [maxAttempts] accordingly for expensive
 * models.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMillis: Long = 500,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMillis: Long = 10_000,
    val jitterFactor: Double = 0.2,
    val maxTotalElapsedMillis: Long? = 60_000,
    val respectRetryAfter: Boolean = true,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(initialBackoffMillis >= 0) { "initialBackoffMillis must be >= 0" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
        require(maxBackoffMillis >= initialBackoffMillis) { "maxBackoffMillis must be >= initialBackoffMillis" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be within [0.0, 1.0]" }
    }

    companion object {
        fun default(): RetryPolicy = RetryPolicy()
        fun noRetry(): RetryPolicy = RetryPolicy(maxAttempts = 1, maxTotalElapsedMillis = null)
    }
}
