/**
 * Base error class for all Kokibot errors
 * Extends native Error with additional context and metadata
 */
class KokibotError extends Error {
    /**
     * @param {string} message - Human-readable error message
     * @param {Object} options - Error metadata
     * @param {Error} [options.cause] - Original error that caused this
     * @param {string} [options.code] - Error code for programmatic handling
     * @param {boolean} [options.recoverable=true] - Can user recover from this?
     * @param {boolean} [options.retryable=false] - Should we auto-retry?
     * @param {Object} [options.context] - Additional context (userId, agentName, etc.)
     * @param {number} [options.timestamp] - When error occurred
     */
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
        super(message, {
            code: 'CONNECTION_ERROR',
            retryable: true,
            ...options
        });
    }
}

class DisconnectionError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'DISCONNECTION_ERROR',
            retryable: true,
            ...options
        });
    }
}

class TimeoutError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'TIMEOUT_ERROR',
            retryable: true,
            ...options
        });
        this.duration = options.duration || null;
        this.timeout = options.timeout || null;
    }
}

class RateLimitError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'RATE_LIMIT_ERROR',
            retryable: true,
            recoverable: false,
            ...options
        });
        this.retryAfter = options.retryAfter || null;
    }
}

class ValidationError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'VALIDATION_ERROR',
            retryable: false,
            recoverable: true,
            ...options
        });
        this.field = options.field || null;
        this.value = options.value || null;
        this.constraints = options.constraints || {};
    }
}

class FileError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'FILE_ERROR',
            retryable: false,
            ...options
        });
        this.fileName = options.fileName || null;
        this.fileSize = options.fileSize || null;
        this.fileType = options.fileType || null;
        this.reason = options.reason || null;
    }
}

class StateError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'STATE_ERROR',
            retryable: false,
            recoverable: false,
            ...options
        });
        this.currentState = options.currentState || null;
        this.expectedState = options.expectedState || null;
    }
}

class ServerError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'SERVER_ERROR',
            retryable: true,
            recoverable: false,
            ...options
        });
        this.statusCode = options.statusCode || null;
        this.endpoint = options.endpoint || null;
    }
}

class NotFoundError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'NOT_FOUND_ERROR',
            retryable: false,
            recoverable: false,
            ...options
        });
        this.resource = options.resource || null;
        this.resourceId = options.resourceId || null;
    }
}

class AuthenticationError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'AUTH_ERROR',
            retryable: false,
            recoverable: true,
            ...options
        });
        this.statusCode = options.statusCode || 401;
    }
}

class JSONParseError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'JSON_PARSE_ERROR',
            retryable: false,
            recoverable: false,
            ...options
        });
        this.rawData = options.rawData || null;
    }
}

class RenderError extends KokibotError {
    constructor(message, options = {}) {
        super(message, {
            code: 'RENDER_ERROR',
            retryable: false,
            recoverable: true,
            ...options
        });
        this.content = options.content || null;
        this.renderer = options.renderer || 'markdown';
    }
}

export {
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
};
