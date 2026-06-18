const ContextWindowDisplay = {
    agentName: null,
    conversationId: null,

    init(agentName) {
        this.agentName = agentName;
    },

    reset() {
        this.conversationId = null;
        this.refresh();
    },

    async refresh(conversationId) {
        this.conversationId = conversationId ?? this.conversationId;
        try {
            let url = `/assistants/${this.agentName}/context-window?userId=anonymous&channelId=channel:websocket`;
            if (this.conversationId) url += `&conversationId=${encodeURIComponent(this.conversationId)}`;
            const response = await fetch(url);
            if (!response.ok) return;
            const data = await response.json();
            this._render(data.baseline, data.max);
        } catch (e) {
            console.warn('Failed to fetch context window:', e);
        }
    },

    _formatTokens(value) {
        if (value <= 1000) return '1Kb';
        if (value < 1_000_000) return Math.round(value / 1000) + 'Kb';
        const m = value / 1_000_000;
        return (Number.isInteger(m) ? m : parseFloat(m.toFixed(1))) + 'Mb';
    },

    _render(baseline, max) {
        const bar = document.getElementById('context-window-bar');
        const text = document.getElementById('context-window-text');
        const baselineEl = document.getElementById('context-window-baseline');
        if (!bar || !text) return;

        const pct = max > 0 ? Math.min(Math.round((baseline / max) * 100), 100) : 0;
        bar.style.width = `${pct}%`;

        bar.classList.remove('yellow', 'red');
        if (pct > 75) {
            bar.classList.add('red');
        } else if (pct > 50) {
            bar.classList.add('yellow');
        }

        text.textContent = `${pct}%`;
        if (baselineEl) baselineEl.textContent = this._formatTokens(baseline);
    },
};
