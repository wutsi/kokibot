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

    async _loadAgentsList(agentName) {
        const listEl = document.getElementById('agents-list');
        if (!listEl) return;

        try {
            const res = await fetch('/assistants');
            if (!res.ok) return;
            const agents = await res.json();

            let html = '';
            for (const name of agents) {
                if (name === agentName) continue;
                html += `<button class="agent-list-item" data-agent="${escapeHtml(name)}">
                    <div class="agent-avatar agent-avatar--sm">${this._avatarContent(name)}</div>
                    <span class="agent-list-name">${escapeHtml(this._formatName(name))}</span>
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
        return name.split(/[-_]/).map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    },

    _initials(name) {
        const parts = name.split(/[-_\s]+/).filter(Boolean);
        if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
        return name.substring(0, 2).toUpperCase();
    },
};
