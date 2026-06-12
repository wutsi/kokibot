/**
 * Sidebar Component
 * Manages left sidebar with navigation menu (New Chat, History, Settings)
 * Handles collapsing/expanding and navigation actions
 */
const Sidebar = {
    sidebar: null,
    toggleButton: null,
    historyButton: null,
    settingsButton: null,
    storageKey: 'kokibot_sidebar_collapsed',

    init() {
        this.setupElements();
        this.loadState();
        this.setupEventListeners();
        ConversationHistory.init(getAgentNameFromURL());
    },

    setupElements() {
        this.sidebar = document.getElementById('sidebar');
        this.toggleButton = document.getElementById('sidebar-toggle');
        this.newChatButton = document.getElementById('new-chat-btn');
        this.historyButton = document.getElementById('history-btn');
        this.settingsButton = document.getElementById('settings-btn');
    },

    loadState() {
        // Load saved state from localStorage (default: expanded)
        const isCollapsed = localStorage.getItem(this.storageKey) === 'true';
        if (isCollapsed) {
            this.sidebar.classList.add('collapsed');
        }
    },

    setupEventListeners() {
        // Toggle button
        this.toggleButton.addEventListener('click', () => {
            this.toggle();
        });

        // New Chat button
        if (this.newChatButton) {
            this.newChatButton.addEventListener('click', () => {
                ChatUI.newChat();
            });
        }

        // History button (disabled for now)
        if (this.historyButton) {
            this.historyButton.addEventListener('click', () => {
                this.handleHistory();
            });
        }

        // Settings button
        if (this.settingsButton) {
            this.settingsButton.addEventListener('click', () => {
                this.handleSettings();
            });
        }
    },

    toggle() {
        const isCollapsed = this.sidebar.classList.toggle('collapsed');
        // Save state to localStorage
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

    handleHistory() {
        // Placeholder for future history feature
        Notifications.info('Chat history feature coming soon!');
    },

    handleSettings() {
        // Navigate to settings page with agent parameter
        const agentName = getAgentNameFromURL();
        window.location.href = `/settings.html?agent=${agentName}`;
    }
};
