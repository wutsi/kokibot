/**
 * Sidebar Component
 * Manages left sidebar with 4 sections:
 *   1. Current agent (icon, name, New Chat, Settings)
 *   2. Agents list (from /assistants)
 *   3. Chat history (scrollable, up to 30)
 *   4. Status (connection + context window)
 */
const Sidebar = {
    sidebar: null,
    toggleButton: null,
    newChatButton: null,
    settingsButton: null,
    storageKey: 'kokibot_sidebar_collapsed',

    init() {
        this.setupElements();
        this.loadState();
        this.setupEventListeners();
        const agentName = getAgentNameFromURL();
        this._loadCurrentAgent(agentName);
        this._loadAgentsList(agentName);
        this._loadLLMInfo(agentName);
        ConversationHistory.init(agentName);
    },

    setupElements() {
        this.sidebar = document.getElementById('sidebar');
        this.toggleButton = document.getElementById('sidebar-toggle');
        this.newChatButton = document.getElementById('new-chat-btn');
        this.settingsButton = document.getElementById('settings-btn');
    },

    loadState() {
        const isCollapsed = localStorage.getItem(this.storageKey) === 'true';
        if (isCollapsed) this.sidebar.classList.add('collapsed');
    },

    setupEventListeners() {
        this.toggleButton.addEventListener('click', () => this.toggle());
        if (this.newChatButton) this.newChatButton.addEventListener('click', () => ChatUI.newChat());
        if (this.settingsButton) this.settingsButton.addEventListener('click', () => this.handleSettings());
    },

    toggle() {
        const isCollapsed = this.sidebar.classList.toggle('collapsed');
        localStorage.setItem(this.storageKey, isCollapsed.toString());
    },

    expand() {
        this.sidebar.classList.remove('collapsed');
        localStorage.setItem(this.storageKey, 'false');
    },

    collapse() {
        this.sidebar.classList.add('collapsed');
        localStorage.setItem(this.storageKey, 'true');
    },

    handleSettings() {
        const agentName = getAgentNameFromURL();
        window.location.href = `/settings.html?agent=${agentName}`;
    },

    async _loadCurrentAgent(agentName) {
        const avatarEl = document.getElementById('sidebar-agent-avatar');
        const nameEl = document.getElementById('sidebar-agent-name');
        if (!avatarEl || !nameEl) return;

        avatarEl.innerHTML = this._avatarContent(agentName);
        nameEl.textContent = this._formatName(agentName);

        try {
            const res = await fetch(`/assistants/${agentName}`);
            if (res.ok) {
                const data = await res.json();
                if (data.name) nameEl.textContent = this._formatName(data.name);
            }
        } catch (_) {}
    },

    async _loadLLMInfo(agentName) {
        const statusEl = document.getElementById('llm-status');
        const iconEl = document.getElementById('llm-status-icon');
        const modelEl = document.getElementById('llm-status-model');
        if (!statusEl || !iconEl || !modelEl) return;

        try {
            const res = await fetch(`/assistants/${agentName}/llm`);
            if (!res.ok) return;
            const data = await res.json();
            const name = data.name;
            const model = data.model;
            if (!name || name === 'null') return;

            iconEl.src = `/assets/llm/${name}.png`;
            iconEl.alt = name;
            iconEl.onerror = () => { iconEl.style.display = 'none'; };
            modelEl.textContent = model || name;
            statusEl.style.display = '';
        } catch (_) {}
    },

    async _loadAgentsList(agentName) {
        const listEl = document.getElementById('agents-list');
        if (!listEl) return;

        try {
            const res = await fetch(`/assistants?limit=3&exclude=${encodeURIComponent(agentName)}&enabled=true`);
            if (!res.ok) return;
            const agents = await res.json();

            const section = listEl.closest('.sidebar-section--agents');
            const divider = document.getElementById('agents-section-divider');
            if (agents.length === 0) {
                if (section) section.style.display = 'none';
                if (divider) divider.style.display = 'none';
                return;
            }
            if (section) section.style.display = '';
            if (divider) divider.style.display = '';

            const totalCount = res.headers.get('X-Total-Count');
            const otherCount = totalCount !== null ? Math.max(0, parseInt(totalCount, 10)) : agents.length;
            const totalWithCurrent = otherCount;

            const viewAllBtn = document.getElementById('view-all-agents-btn');
            const viewAllLabel = document.getElementById('view-all-agents-label');
            viewAllLabel.textContent = `All Assistants (${totalWithCurrent})`;

            let html = '';
            for (const agent of agents) {
                html += `<button class="agent-list-item" data-agent="${escapeHtml(agent.name)}">
                    <div class="agent-avatar agent-avatar--sm">${this._avatarContent(agent.name)}</div>
                    <span class="agent-list-name">${escapeHtml(this._formatName(agent.name))}</span>
                </button>`;
            }
            listEl.innerHTML = html;

            listEl.addEventListener('click', (e) => {
                const btn = e.target.closest('.agent-list-item');
                if (!btn) return;
                const target = btn.dataset.agent;
                if (target !== agentName) {
                    window.location.href = `/index.html?agent=${encodeURIComponent(target)}`;
                }
            });
        } catch (_) {
            console.warn('Failed to load agents list');
        }
    },

    _avatarContent(name) {
        const initials = escapeHtml(this._initials(name));
        return `<img src="/assistants/${encodeURIComponent(name)}/icon.png" alt="" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'"><span style="display:none">${initials}</span>`;
    },

    _formatName(name) {
        //return name.split(/[-_]/).map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
        return name;
    },

    _initials(name) {
        const parts = name.split(/[-_\s]+/).filter(Boolean);
        if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
        return name.substring(0, 2).toUpperCase();
    },
};
