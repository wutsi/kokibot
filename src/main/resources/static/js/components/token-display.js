/**
 * Token usage display component
 * Shows current interaction tokens on left, accumulated total on right
 */
class TokenDisplay {
    constructor() {
        this.currentUsage = null;
        this.accumulatedUsage = null;
        this.currentElapsed = null;
        this.accumulatedElapsed = 0;
    }

    reset() {
        this.currentUsage = null;
        this.currentElapsed = null;
    }

    /**
     * Update token display with new usage
     */
    update(messageElement, usage) {
        this.currentUsage = {
            totalTokens: usage.totalTokens || 0,
            promptTokens: usage.promptTokens || 0,
            completionTokens: usage.completionTokens || 0,
            promptCacheHitTokens: usage.promptCacheHitTokens || 0
        };

        if (!this.accumulatedUsage) {
            this.accumulatedUsage = { totalTokens: 0, promptTokens: 0, completionTokens: 0, promptCacheHitTokens: 0 };
        }
        this.accumulatedUsage.totalTokens += this.currentUsage.totalTokens;
        this.accumulatedUsage.promptTokens += this.currentUsage.promptTokens;
        this.accumulatedUsage.completionTokens += this.currentUsage.completionTokens;
        this.accumulatedUsage.promptCacheHitTokens += this.currentUsage.promptCacheHitTokens;

        this._renderTo(messageElement);
    }

    updateLiveElapsed(messageElement, elapsedMs) {
        this.currentElapsed = elapsedMs;
        this._renderTo(messageElement);
    }

    finalize(messageElement, elapsedMs) {
        this.currentElapsed = elapsedMs;
        this.accumulatedElapsed += elapsedMs;
        this._renderTo(messageElement);
    }

    _renderTo(messageElement) {
        const contentDiv = messageElement.querySelector('.message-content');
        if (!contentDiv) return;
        let tokenDisplay = contentDiv.querySelector('.token-display');
        if (!tokenDisplay) {
            tokenDisplay = document.createElement('div');
            tokenDisplay.className = 'token-display';
            const timestamp = contentDiv.querySelector('.message-timestamp');
            timestamp ? contentDiv.insertBefore(tokenDisplay, timestamp) : contentDiv.appendChild(tokenDisplay);
        }
        tokenDisplay.innerHTML = this.buildDisplayHTML();
    }

    buildDisplayHTML() {
        const currentHTML = this.buildSectionHTML(this.currentUsage, this.currentElapsed);
        const showTotal = this.accumulatedUsage !== null || this.accumulatedElapsed > 0;
        if (!showTotal) {
            return `
                <div class="token-section token-section-current">
                    <div class="token-section-label">Interaction</div>
                    <div class="token-section-value">${currentHTML}</div>
                </div>
            `;
        }
        const accumulatedHTML = this.buildSectionHTML(this.accumulatedUsage, null);
        return `
            <div class="token-section token-section-current">
                <div class="token-section-label">Interaction</div>
                <div class="token-section-value">${currentHTML}</div>
            </div>
            <div class="token-section-divider"></div>
            <div class="token-section token-section-accumulated">
                <div class="token-section-label">Total</div>
                <div class="token-section-value">${accumulatedHTML}</div>
            </div>
        `;
    }

    formatElapsed(ms) {
        if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
        const m = Math.floor(ms / 60000);
        const s = Math.round((ms % 60000) / 1000);
        return `${m}m ${s}s`;
    }

    buildSectionHTML(usage, elapsed) {
        const parts = [];

        if (elapsed !== null && elapsed !== undefined && elapsed > 0) {
            parts.push(`<span class="token-elapsed">⏱ ${this.formatElapsed(elapsed)}</span>`);
        }

        if (!usage) return parts.length ? parts.join(' ') : '<span class="token-total">—</span>';

        if (usage.totalTokens > 0) {
            parts.push(`<span class="token-total">${this.formatTokenCount(usage.totalTokens)} tokens</span>`);
        }

        const details = [];
        if (usage.promptTokens > 0) details.push(`↑${this.formatTokenCount(usage.promptTokens)}`);
        if (usage.completionTokens > 0) details.push(`↓${this.formatTokenCount(usage.completionTokens)}`);
        if (details.length > 0) {
            parts.push(`<span class="token-details">(${details.join(', ')})</span>`);
        }

        if (usage.promptCacheHitTokens > 0) {
            parts.push(`<span class="token-cached">🛢️${this.formatTokenCount(usage.promptCacheHitTokens)}</span>`);
        }

        return parts.join(' ');
    }

    /**
     * Format token count (1000 → 1K)
     */
    formatTokenCount(count) {
        if (count >= 1_000_000) {
            const m = count / 1_000_000;
            const formatted = m.toFixed(1);
            return (formatted.endsWith('.0') ? formatted.slice(0, -2) : formatted) + 'M';
        }
        if (count >= 1000) {
            const k = count / 1000;
            const formatted = k.toFixed(1);
            return (formatted.endsWith('.0') ? formatted.slice(0, -2) : formatted) + 'K';
        }
        return count.toString();
    }
}
