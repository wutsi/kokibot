/**
 * Retry manager with exponential backoff and jitter
 */
export class RetryManager {
    /**
     * Retry an async operation with exponential backoff
     *
     * @param {Function} operation - Async function to retry
     * @param {Object} options - Retry configuration
     * @param {number} [options.maxAttempts=3] - Max retry attempts
     * @param {number} [options.initialDelay=1000] - Initial delay in ms
     * @param {number} [options.maxDelay=30000] - Max delay in ms
     * @param {number} [options.factor=2] - Exponential backoff factor
     * @param {boolean} [options.jitter=true] - Add random jitter
     * @param {Function} [options.shouldRetry] - Custom retry predicate (error) => boolean
     * @param {Function} [options.onRetryAttempt] - Callback on each retry (attempt, delay)
     *
     * @returns {Promise<any>} Operation result
     * @throws {Error} Last error if all attempts fail
     */
    async retry(operation, options = {}) {
        const {
            maxAttempts = 3,
            initialDelay = 1000,
            maxDelay = 30000,
            factor = 2,
            jitter = true,
            shouldRetry = null,
            onRetryAttempt = null
        } = options;

        let lastError;
        let delay = initialDelay;

        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                const result = await operation();
                return result;
            } catch (error) {
                lastError = error;

                if (shouldRetry && !shouldRetry(error)) {
                    throw error;
                }

                if (attempt === maxAttempts) {
                    break;
                }

                const nextDelay = Math.min(delay * factor, maxDelay);

                const actualDelay = jitter
                    ? nextDelay + (Math.random() - 0.5) * 0.5 * nextDelay
                    : nextDelay;

                if (onRetryAttempt) {
                    onRetryAttempt(attempt, Math.round(actualDelay));
                }

                await this._sleep(actualDelay);

                delay = nextDelay;
            }
        }

        throw lastError;
    }

    _sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
