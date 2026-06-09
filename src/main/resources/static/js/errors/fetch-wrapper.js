import { TimeoutError, ServerError, NotFoundError, AuthenticationError } from './error-types.js';

/**
 * Enhanced fetch with timeout, retry, and error classification
 */
export class FetchWrapper {
    /**
     * Fetch with timeout and automatic error wrapping
     *
     * @param {string} url - Request URL
     * @param {Object} options - Fetch options
     * @param {number} [options.timeout=5000] - Request timeout in ms
     * @param {AbortSignal} [options.signal] - Abort signal
     * @returns {Promise<Response>}
     * @throws {TimeoutError|ServerError|NotFoundError|AuthenticationError}
     */
    static async fetchWithTimeout(url, options = {}) {
        const timeout = options.timeout || 5000;

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        const signal = options.signal
            ? this._mergeSignals(options.signal, controller.signal)
            : controller.signal;

        try {
            const startTime = Date.now();
            const response = await fetch(url, {
                ...options,
                signal
            });
            const duration = Date.now() - startTime;

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw await this._createErrorFromResponse(response, url);
            }

            return response;

        } catch (error) {
            clearTimeout(timeoutId);

            if (error.name === 'AbortError') {
                throw new TimeoutError(`Request to ${url} timed out`, {
                    timeout,
                    url,
                    cause: error
                });
            }

            throw error;
        }
    }

    static async _createErrorFromResponse(response, url) {
        const status = response.status;
        let message;

        try {
            const body = await response.json();
            message = body.message || body.error || response.statusText;
        } catch {
            message = response.statusText;
        }

        if (status === 404) {
            return new NotFoundError(message, {
                statusCode: status,
                endpoint: url
            });
        }

        if (status === 401 || status === 403) {
            return new AuthenticationError(message, {
                statusCode: status,
                endpoint: url
            });
        }

        if (status >= 500) {
            return new ServerError(message, {
                statusCode: status,
                endpoint: url
            });
        }

        return new Error(`HTTP ${status}: ${message}`);
    }

    static _mergeSignals(...signals) {
        const controller = new AbortController();

        for (const signal of signals) {
            if (signal.aborted) {
                controller.abort();
                break;
            }
            signal.addEventListener('abort', () => controller.abort());
        }

        return controller.signal;
    }
}
