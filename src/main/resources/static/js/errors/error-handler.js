import {
    KokibotError,
    ConnectionError,
    DisconnectionError,
    TimeoutError,
    RateLimitError,
    ValidationError,
    FileError,
    StateError,
    ServerError,
    NotFoundError,
    AuthenticationError,
    JSONParseError,
    RenderError
} from './error-types.js';

import { ErrorUI } from './error-ui.js';
import { RetryManager } from './retry-manager.js';
import { CircuitBreaker } from './circuit-breaker.js';

/**
 * Centralized error handling singleton
 */
class ErrorHandler {
    constructor() {
        if (ErrorHandler.instance) {
            return ErrorHandler.instance;
        }

        this.errorUI = new ErrorUI();
        this.retryManager = new RetryManager();
        this.circuitBreakers = new Map();
        this.errorLog = [];
        this.errorCounts = new Map();
        this.listeners = new Set();

        this.sentryEnabled = false;
        this.analyticsEnabled = false;
        this.logger = console;
        this.maxLogSize = 100;

        ErrorHandler.instance = this;
    }

    configure(config = {}) {
        if (config.sentry) {
            this.sentryEnabled = true;
            this.sentry = config.sentry;
        }

        if (config.analytics !== undefined) {
            this.analyticsEnabled = config.analytics;
        }

        if (config.logger) {
            this.logger = config.logger;
        }

        if (config.maxLogSize) {
            this.maxLogSize = config.maxLogSize;
        }
    }

    async handle(error, options = {}) {
        const wrappedError = this._wrapError(error, options);

        wrappedError.context = {
            ...wrappedError.context,
            ...options.metadata,
            context: options.context,
            operation: options.operation,
            url: window.location.href,
            userAgent: navigator.userAgent
        };

        this._logError(wrappedError);
        this._sendToExternalServices(wrappedError);
        this._trackMetrics(wrappedError);
        this._notifyListeners(wrappedError);

        const recoveryResult = await this._attemptRecovery(wrappedError, options);

        if (!options.silent && !recoveryResult.recovered) {
            this._showErrorUI(wrappedError, options, recoveryResult);
        }

        return recoveryResult;
    }

    _wrapError(error, options) {
        if (error instanceof KokibotError) {
            return error;
        }

        if (error.name === 'TypeError' && error.message.includes('fetch')) {
            return new ConnectionError('Network request failed', {
                cause: error,
                context: options.metadata
            });
        }

        if (error.name === 'SyntaxError' && error.message.includes('JSON')) {
            return new JSONParseError('Failed to parse JSON response', {
                cause: error,
                context: options.metadata
            });
        }

        return new KokibotError(error.message || 'Unknown error', {
            cause: error,
            context: options.metadata
        });
    }

    _logError(error) {
        this.logger.error(`[${error.name}] ${error.message}`, error.toJSON());

        this.errorLog.push(error.toJSON());
        if (this.errorLog.length > this.maxLogSize) {
            this.errorLog.shift();
        }

        const count = this.errorCounts.get(error.code) || 0;
        this.errorCounts.set(error.code, count + 1);
    }

    _sendToExternalServices(error) {
        if (this.sentryEnabled && typeof Sentry !== 'undefined') {
            Sentry.captureException(error, {
                tags: {
                    errorCode: error.code,
                    context: error.context.context,
                    operation: error.context.operation
                },
                extra: error.context
            });
        }

        if (this.analyticsEnabled && typeof gtag !== 'undefined') {
            gtag('event', 'exception', {
                description: `${error.name}: ${error.message}`,
                fatal: !error.recoverable,
                error_code: error.code
            });
        }
    }

    _trackMetrics(error) {
        const metricKey = `error.${error.code}`;
        const count = this.errorCounts.get(metricKey) || 0;
        this.errorCounts.set(metricKey, count + 1);
    }

    _notifyListeners(error) {
        this.listeners.forEach(listener => {
            try {
                listener(error);
            } catch (err) {
                this.logger.error('Error in error listener:', err);
            }
        });
    }

    async _attemptRecovery(error, options) {
        if (!error.retryable) {
            return { recovered: false, method: 'none', error };
        }

        const operationKey = `${options.context}.${options.operation}`;
        const breaker = this._getCircuitBreaker(operationKey);

        if (breaker.isOpen()) {
            this.logger.warn(`Circuit breaker OPEN for ${operationKey}, skipping retry`);
            return { recovered: false, method: 'circuit_breaker_open', error };
        }

        if (options.onRetry) {
            try {
                const result = await this.retryManager.retry(
                    options.onRetry,
                    {
                        maxAttempts: this._getMaxRetries(error),
                        initialDelay: this._getInitialDelay(error),
                        maxDelay: 30000,
                        factor: 2,
                        jitter: true,
                        onRetryAttempt: (attempt, delay) => {
                            this.logger.info(`Retry attempt ${attempt} in ${delay}ms`);
                        }
                    }
                );

                breaker.recordSuccess();
                return { recovered: true, method: 'retry', result };
            } catch (retryError) {
                breaker.recordFailure();

                if (options.onFallback) {
                    try {
                        const result = await options.onFallback();
                        return { recovered: true, method: 'fallback', result };
                    } catch (fallbackError) {
                        this.logger.error('Fallback failed:', fallbackError);
                    }
                }

                return { recovered: false, method: 'retry_exhausted', error: retryError };
            }
        }

        if (options.onFallback) {
            try {
                const result = await options.onFallback();
                return { recovered: true, method: 'fallback', result };
            } catch (fallbackError) {
                this.logger.error('Fallback failed:', fallbackError);
                return { recovered: false, method: 'fallback_failed', error: fallbackError };
            }
        }

        return { recovered: false, method: 'no_recovery_options', error };
    }

    _getCircuitBreaker(operationKey) {
        if (!this.circuitBreakers.has(operationKey)) {
            this.circuitBreakers.set(operationKey, new CircuitBreaker({
                failureThreshold: 5,
                successThreshold: 2,
                timeout: 60000,
                monitoringPeriod: 30000
            }));
        }
        return this.circuitBreakers.get(operationKey);
    }

    _getMaxRetries(error) {
        if (error instanceof TimeoutError) return 2;
        if (error instanceof ConnectionError) return 3;
        if (error instanceof DisconnectionError) return 5;
        if (error instanceof ServerError) return 3;
        if (error instanceof RateLimitError) return 1;
        return 3;
    }

    _getInitialDelay(error) {
        if (error instanceof RateLimitError && error.retryAfter) {
            return error.retryAfter * 1000;
        }
        if (error instanceof TimeoutError) return 2000;
        if (error instanceof ServerError) return 5000;
        return 1000;
    }

    _showErrorUI(error, options, recoveryResult) {
        const userMessage = options.userMessage || this._getUserMessage(error);
        const actions = this._getErrorActions(error, options, recoveryResult);
        const uiType = this._getUIType(error);

        switch (uiType) {
            case 'toast':
                this.errorUI.showToast(userMessage, {
                    severity: error.recoverable ? 'warning' : 'error',
                    duration: 5000,
                    actions
                });
                break;

            case 'inline':
                this.errorUI.showInline(userMessage, {
                    severity: 'error',
                    target: options.target,
                    actions
                });
                break;

            case 'modal':
                this.errorUI.showModal(userMessage, {
                    title: error.name,
                    severity: 'error',
                    actions,
                    dismissible: error.recoverable
                });
                break;

            case 'status-bar':
                this.errorUI.showStatusBar(userMessage, {
                    severity: 'error',
                    persistent: true,
                    actions
                });
                break;
        }
    }

    _getUserMessage(error) {
        if (error instanceof ConnectionError) {
            return "Can't connect to server. Check your internet connection.";
        }

        if (error instanceof DisconnectionError) {
            return "Connection lost. Attempting to reconnect...";
        }

        if (error instanceof TimeoutError) {
            return `Request timed out after ${Math.round(error.timeout / 1000)}s. Please try again.`;
        }

        if (error instanceof RateLimitError) {
            const wait = error.retryAfter || 60;
            return `Too many requests. Please wait ${wait} seconds.`;
        }

        if (error instanceof ValidationError) {
            return error.message;
        }

        if (error instanceof FileError) {
            if (error.reason === 'too_large') {
                return `File "${error.fileName}" is too large (5MB max).`;
            }
            if (error.reason === 'invalid_type') {
                return `File type not supported for "${error.fileName}".`;
            }
            return `Failed to upload "${error.fileName}". Please try again.`;
        }

        if (error instanceof NotFoundError) {
            if (error.resource === 'agent') {
                return `Agent "${error.resourceId}" not found. Switching to default agent.`;
            }
            return `${error.resource} not found.`;
        }

        if (error instanceof ServerError) {
            return `Server error (${error.statusCode}). Our team has been notified.`;
        }

        if (error instanceof AuthenticationError) {
            return "Authentication failed. Please reload and try again.";
        }

        if (error instanceof JSONParseError) {
            return "Received invalid data from server. Please try again.";
        }

        if (error instanceof RenderError) {
            return "Message displayed as plain text (formatting failed).";
        }

        return error.recoverable
            ? "Something went wrong. Please try again."
            : "An unexpected error occurred. Please reload the page.";
    }

    _getUIType(error) {
        if (error instanceof DisconnectionError || error instanceof ConnectionError) {
            return 'status-bar';
        }

        if (error instanceof ValidationError || error instanceof FileError) {
            return 'inline';
        }

        if (error instanceof AuthenticationError || error instanceof StateError) {
            return 'modal';
        }

        return 'toast';
    }

    _getErrorActions(error, options, recoveryResult) {
        const actions = [];

        if (error.retryable && options.onRetry && !recoveryResult.method.includes('exhausted')) {
            actions.push({
                label: 'Retry',
                primary: true,
                handler: async () => {
                    try {
                        await options.onRetry();
                        this.errorUI.dismiss();
                    } catch (err) {
                        this.handle(err, { ...options, silent: false });
                    }
                }
            });
        }

        if (options.onFallback) {
            actions.push({
                label: 'Use Alternative',
                handler: async () => {
                    try {
                        await options.onFallback();
                        this.errorUI.dismiss();
                    } catch (err) {
                        this.handle(err, { ...options, silent: false });
                    }
                }
            });
        }

        if (!error.recoverable) {
            actions.push({
                label: 'Reload Page',
                primary: true,
                handler: () => window.location.reload()
            });
        }

        actions.push({
            label: error.recoverable ? 'Cancel' : 'OK',
            handler: () => {
                this.errorUI.dismiss();
                if (options.onFail) {
                    options.onFail(error);
                }
            }
        });

        return actions;
    }

    addEventListener(listener) {
        this.listeners.add(listener);
    }

    removeEventListener(listener) {
        this.listeners.delete(listener);
    }

    getStats() {
        return {
            totalErrors: this.errorLog.length,
            errorsByCode: Object.fromEntries(this.errorCounts),
            recentErrors: this.errorLog.slice(-10)
        };
    }

    clearHistory() {
        this.errorLog = [];
        this.errorCounts.clear();
    }

    getCircuitBreakerStatus(operationKey) {
        const breaker = this.circuitBreakers.get(operationKey);
        return breaker ? breaker.getState() : 'CLOSED';
    }
}

const errorHandler = new ErrorHandler();

export default errorHandler;
export { ErrorHandler };
