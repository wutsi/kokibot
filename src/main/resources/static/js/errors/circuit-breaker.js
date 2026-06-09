/**
 * Circuit breaker pattern implementation
 * Prevents cascading failures by failing fast when error rate is high
 */
export class CircuitBreaker {
    /**
     * @param {Object} options
     * @param {number} [options.failureThreshold=5] - Open after N failures
     * @param {number} [options.successThreshold=2] - Close after N successes (in half-open)
     * @param {number} [options.timeout=60000] - Try again after N ms
     * @param {number} [options.monitoringPeriod=30000] - Count failures in N ms window
     */
    constructor(options = {}) {
        this.failureThreshold = options.failureThreshold || 5;
        this.successThreshold = options.successThreshold || 2;
        this.timeout = options.timeout || 60000;
        this.monitoringPeriod = options.monitoringPeriod || 30000;

        this.state = 'CLOSED';
        this.failures = [];
        this.successes = 0;
        this.nextAttemptTime = null;
    }

    recordSuccess() {
        if (this.state === 'HALF_OPEN') {
            this.successes++;

            if (this.successes >= this.successThreshold) {
                this._transitionTo('CLOSED');
            }
        } else if (this.state === 'CLOSED') {
            this._cleanupFailures();
        }
    }

    recordFailure() {
        const now = Date.now();
        this.failures.push(now);
        this._cleanupFailures();

        if (this.state === 'HALF_OPEN') {
            this._transitionTo('OPEN');
        } else if (this.state === 'CLOSED') {
            if (this.failures.length >= this.failureThreshold) {
                this._transitionTo('OPEN');
            }
        }
    }

    isOpen() {
        if (this.state === 'OPEN') {
            if (Date.now() >= this.nextAttemptTime) {
                this._transitionTo('HALF_OPEN');
                return false;
            }
            return true;
        }
        return false;
    }

    getState() {
        if (this.state === 'OPEN' && Date.now() >= this.nextAttemptTime) {
            this._transitionTo('HALF_OPEN');
        }
        return this.state;
    }

    _transitionTo(newState) {
        console.log(`Circuit breaker: ${this.state} -> ${newState}`);
        this.state = newState;

        if (newState === 'OPEN') {
            this.nextAttemptTime = Date.now() + this.timeout;
        } else if (newState === 'HALF_OPEN') {
            this.successes = 0;
        } else if (newState === 'CLOSED') {
            this.failures = [];
            this.successes = 0;
            this.nextAttemptTime = null;
        }
    }

    _cleanupFailures() {
        const cutoff = Date.now() - this.monitoringPeriod;
        this.failures = this.failures.filter(time => time > cutoff);
    }
}
