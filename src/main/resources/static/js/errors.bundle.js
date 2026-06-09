/**
 * Error Handling System - Bundled for non-module usage
 * This file exports error handling to window.KokibotErrors
 */

(function() {
    'use strict';

    // Error Types
    class KokibotError extends Error {
        constructor(message, options = {}) {
            super(message);
            this.name = this.constructor.name;
            this.code = options.code || 'UNKNOWN_ERROR';
            this.recoverable = options.recoverable !== false;
            this.retryable = options.retryable || false;
            this.context = options.context || {};
            this.timestamp = options.timestamp || Date.now();
            this.cause = options.cause || null;

            if (Error.captureStackTrace) {
                Error.captureStackTrace(this, this.constructor);
            }

            if (this.cause && this.cause.stack) {
                this.stack += '\nCaused by: ' + this.cause.stack;
            }
        }

        toJSON() {
            return {
                name: this.name,
                message: this.message,
                code: this.code,
                recoverable: this.recoverable,
                retryable: this.retryable,
                context: this.context,
                timestamp: this.timestamp,
                stack: this.stack,
                cause: this.cause ? {
                    name: this.cause.name,
                    message: this.cause.message,
                    stack: this.cause.stack
                } : null
            };
        }
    }

    class ConnectionError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'CONNECTION_ERROR', retryable: true, ...options });
        }
    }

    class DisconnectionError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'DISCONNECTION_ERROR', retryable: true, ...options });
        }
    }

    class TimeoutError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'TIMEOUT_ERROR', retryable: true, ...options });
            this.duration = options.duration || null;
            this.timeout = options.timeout || null;
        }
    }

    class FileError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'FILE_ERROR', retryable: false, ...options });
            this.fileName = options.fileName || null;
            this.fileSize = options.fileSize || null;
            this.reason = options.reason || null;
        }
    }

    class ServerError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'SERVER_ERROR', retryable: true, recoverable: false, ...options });
            this.statusCode = options.statusCode || null;
            this.endpoint = options.endpoint || null;
        }
    }

    class JSONParseError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'JSON_PARSE_ERROR', retryable: false, recoverable: false, ...options });
            this.rawData = options.rawData || null;
        }
    }

    class RenderError extends KokibotError {
        constructor(message, options = {}) {
            super(message, { code: 'RENDER_ERROR', retryable: false, recoverable: true, ...options });
            this.content = options.content || null;
            this.renderer = options.renderer || 'markdown';
        }
    }

    // Export error types
    window.KokibotErrors = {
        KokibotError,
        ConnectionError,
        DisconnectionError,
        TimeoutError,
        FileError,
        ServerError,
        JSONParseError,
        RenderError
    };

    console.log('Kokibot Error System loaded');
})();
