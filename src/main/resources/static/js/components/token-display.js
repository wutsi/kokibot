/**
 * Token usage display component
 * Accumulates and formats token metrics
 */
class TokenDisplay {
    constructor() {
        this.accumulatedUsage = null;
    }

    /**
     * Reset for new message
     */
    reset() {
        this.accumulatedUsage = null;
    }

    /**
     * Update token display with new usage
     */
    update(messageElement, usage) {
        if (!this.accumulatedUsage) {
            this.accumulatedUsage = {
                totalTokens: 0,
                promptTokens: 0,
                completionTokens: 0,
                promptCacheHitTokens: 0
            };
        }

        this.accumulatedUsage.totalTokens += usage.totalTokens || 0;
        this.accumulatedUsage.promptTokens += usage.promptTokens || 0;
        this.accumulatedUsage.completionTokens += usage.completionTokens || 0;
        this.accumulatedUsage.promptCacheHitTokens += (usage.promptCacheHitTokens || 0);

        const contentDiv = messageElement.querySelector('.message-content');

        let tokenDisplay = contentDiv.querySelector('.token-display');
        if (!tokenDisplay) {
            tokenDisplay = document.createElement('div');
            tokenDisplay.className = 'token-display';

            const timestamp = contentDiv.querySelector('.message-timestamp');
            if (timestamp) {
                contentDiv.insertBefore(tokenDisplay, timestamp);
            } else {
                contentDiv.appendChild(tokenDisplay);
            }
        }

        tokenDisplay.innerHTML = this.buildDisplayHTML();
    }

    /**
     * Build token display HTML
     */
    buildDisplayHTML() {
        const parts = [];

        if (this.accumulatedUsage.totalTokens > 0) {
            parts.push(`<span class="token-total">${this.formatTokenCount(this.accumulatedUsage.totalTokens)} tokens</span>`);
        }

        if (this.accumulatedUsage.promptTokens > 0 || this.accumulatedUsage.completionTokens > 0) {
            const details = [];
            if (this.accumulatedUsage.promptTokens > 0) {
                details.push(`${this.formatTokenCount(this.accumulatedUsage.promptTokens)} prompt`);
            }
            if (this.accumulatedUsage.completionTokens > 0) {
                details.push(`${this.formatTokenCount(this.accumulatedUsage.completionTokens)} completion`);
            }
            parts.push(`<span class="token-details">(${details.join(', ')})</span>`);
        }

        if (this.accumulatedUsage.promptCacheHitTokens > 0) {
            parts.push(`<span class="token-cached">💾 ${this.formatTokenCount(this.accumulatedUsage.promptCacheHitTokens)} cached</span>`);
        }

        return parts.join(' ');
    }

    /**
     * Format token count (1000 → 1K)
     */
    formatTokenCount(count) {
        if (count >= 1000) {
            const k = count / 1000;
            const formatted = k.toFixed(1);
            return formatted.endsWith('.0') ? formatted.slice(0, -2) + 'K' : formatted + 'K';
        }
        return count.toString();
    }
}
