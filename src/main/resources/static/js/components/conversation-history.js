/**
 * ConversationHistory Component
 * Fetches and renders past conversations in the sidebar, grouped by date.
 * Loads asynchronously on init — does not block page load.
 */
const ConversationHistory = {
    agentName: null,
    conversations: [],
    listEl: null,

    init(agentName) {
        this.agentName = agentName;
        this.listEl = document.getElementById('conversation-history');
        if (!this.listEl) return;
        this._load();
    },

    setActiveConversation(id) {
        if (!id || !this.listEl) return;
        const inList = this.conversations.some(c => c.id === id);
        if (!inList) {
            this._load().then(() => this._applyActive(id));
        } else {
            this._applyActive(id);
        }
    },

    async _load() {
        if (!this.listEl) return;
        this.listEl.innerHTML = '<div class="conv-loading">Loading…</div>';
        try {
            const res = await fetch(`/assistants/${this.agentName}/conversations?limit=30&offset=0`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            this.conversations = await res.json();
            this._render();
        } catch (e) {
            console.warn('Failed to load conversation list:', e);
            this.listEl.innerHTML = '';
        }
    },

    _render() {
        if (!this.listEl) return;
        const groups = this._groupByDate(this.conversations);
        let html = '';
        for (const [label, items] of groups) {
            if (!items.length) continue;
            html += `<div class="conv-group-label">${label}</div>`;
            for (const conv of items) {
                const safe = this._esc(conv.title);
                html += `<button class="conv-item" data-id="${this._esc(conv.id)}" title="${safe}">${safe}</button>`;
            }
        }
        this.listEl.innerHTML = html;
    },

    _applyActive(id) {
        if (!this.listEl) return;
        this.listEl.querySelectorAll('.conv-item').forEach(el => {
            el.classList.toggle('active', el.dataset.id === id);
        });
    },

    _groupByDate(convs) {
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const yesterday = new Date(today);
        yesterday.setDate(today.getDate() - 1);
        const weekAgo = new Date(today);
        weekAgo.setDate(today.getDate() - 7);

        const todayGroup = [];
        const yesterdayGroup = [];
        const weekGroup = [];
        const olderGroup = [];

        for (const conv of convs) {
            const d = new Date(conv.startDate);
            const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
            if (day >= today) todayGroup.push(conv);
            else if (day >= yesterday) yesterdayGroup.push(conv);
            else if (day >= weekAgo) weekGroup.push(conv);
            else olderGroup.push(conv);
        }

        return [
            ['Today', todayGroup],
            ['Yesterday', yesterdayGroup],
            ['Previous 7 days', weekGroup],
            ['Older', olderGroup],
        ];
    },

    _esc(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    },
};
