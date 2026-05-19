package com.wutsi.kokibot.util.retry

import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException

/**
 * Decides whether a given [Throwable] thrown by an HTTP call is worth retrying.
 *
 * Retryable:
 *  - Transport errors ([IOException], Spring [ResourceAccessException], etc.)
 *  - All HTTP 5xx server errors
 *  - HTTP 429 Too Many Requests (rate limit)
 *
 * Not retryable:
 *  - All other 4xx (400, 401, 403, 404, 422, ...)
 *  - Parsing / business errors (e.g. [IllegalStateException])
 */
object RetryClassifier {
    fun isRetryable(ex: Throwable): Boolean = when (ex) {
        is HttpServerErrorException -> true
        is HttpClientErrorException -> ex.statusCode.value() == 429
        is ResourceAccessException -> true
        is IOException -> true
        else -> false
    }
}
