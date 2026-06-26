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
        this.listEl.addEventListener('click', (e) => {
            const btn = e.target.closest('.conv-item');
            if (btn && btn.dataset.id) this._navigate(btn.dataset.id);
        });
        this._load();
    },

    _navigate(id) {
        const params = new URLSearchParams();
        params.set('agent', this.agentName);
        params.set('conv', id);
        window.location.href = '/index.html?' + params.toString();
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
        const weekStart = new Date(today);
        weekStart.setDate(today.getDate() - ((today.getDay() + 6) % 7)); // Monday
        const thirtyDaysAgo = new Date(today);
        thirtyDaysAgo.setDate(today.getDate() - 30);

        const todayGroup = [];
        const yesterdayGroup = [];
        const thisWeekGroup = [];
        const recentGroup = [];
        const monthGroups = new Map();

        for (const conv of convs) {
            const d = new Date(conv.startDate);
            const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
            if (day >= today) {
                todayGroup.push(conv);
            } else if (day >= yesterday) {
                yesterdayGroup.push(conv);
            } else if (day >= weekStart) {
                thisWeekGroup.push(conv);
            } else if (day >= thirtyDaysAgo) {
                recentGroup.push(conv);
            } else {
                const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
                if (!monthGroups.has(key)) monthGroups.set(key, []);
                monthGroups.get(key).push(conv);
            }
        }

        const groups = [
            ['Today', todayGroup],
            ['Yesterday', yesterdayGroup],
            ['This week', thisWeekGroup],
            ['Previous 30 days', recentGroup],
        ];
        for (const [key, items] of monthGroups) {
            groups.push([key, items]);
        }
        return groups;
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
