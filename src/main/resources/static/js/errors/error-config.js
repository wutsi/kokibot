/**
 * Error handling configuration
 */
export const ERROR_CONFIG = {
    RETRY: {
        MAX_ATTEMPTS: {
            CONNECTION: 3,
            TIMEOUT: 2,
            SERVER_ERROR: 3,
            RATE_LIMIT: 1,
            DEFAULT: 3
        },
        INITIAL_DELAY: {
            CONNECTION: 1000,
            TIMEOUT: 2000,
            SERVER_ERROR: 5000,
            RATE_LIMIT: 60000,
            DEFAULT: 1000
        },
        MAX_DELAY: 30000,
        BACKOFF_FACTOR: 2,
        JITTER: true
    },

    CIRCUIT_BREAKER: {
        FAILURE_THRESHOLD: 5,
        SUCCESS_THRESHOLD: 2,
        TIMEOUT: 60000,
        MONITORING_PERIOD: 30000
    },

    UI: {
        TOAST_DURATION: 5000,
        TOAST_MAX_VISIBLE: 3,
        ANIMATION_DURATION: 300
    },

    FILE: {
        MAX_SIZE: 5 * 1024 * 1024,
        ALLOWED_TYPES: [
            'text/plain',
            'application/pdf',
            'image/png',
            'image/jpeg'
        ]
    },

    TIMEOUTS: {
        FETCH: 5000,
        WEBSOCKET_CONNECT: 10000,
        WEBSOCKET_PING: 30000,
        WEBSOCKET_PONG: 5000
    },

    LOGGING: {
        MAX_ERROR_LOG_SIZE: 100,
        CONSOLE_LOG_LEVEL: 'error',
        SEND_TO_SENTRY: false,
        SEND_TO_ANALYTICS: false
    }
};
