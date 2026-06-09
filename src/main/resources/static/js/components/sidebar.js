/**
 * Sidebar Component
 * Manages left sidebar with navigation menu (New Chat, History, Settings)
 * Handles collapsing/expanding and navigation actions
 */
const Sidebar = {
    sidebar: null,
    toggleButton: null,
    newChatButton: null,
    historyButton: null,
    settingsButton: null,
    storageKey: 'kokibot_sidebar_collapsed',

    init() {
        this.setupElements();
        this.loadState();
        this.setupEventListeners();
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
        this.newChatButton.addEventListener('click', () => {
            this.handleNewChat();
        });

        // History button (disabled for now)
        this.historyButton.addEventListener('click', () => {
            this.handleHistory();
        });

        // Settings button (disabled for now)
        this.settingsButton.addEventListener('click', () => {
            this.handleSettings();
        });
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

    handleNewChat() {
        // Clear chat history and reload page
        if (confirm('Start a new chat? Current conversation will be cleared.')) {
            // Get current agent from URL
            const agentName = getAgentNameFromURL();

            // Clear chat history via API
            fetch(`/assistants/${agentName}/clear`, {
                method: 'POST'
            })
            .then(response => {
                if (response.ok) {
                    // Reload page to start fresh
                    window.location.reload();
                } else {
                    throw new Error('Failed to clear chat');
                }
            })
            .catch(error => {
                console.error('Error clearing chat:', error);
                Notifications.error('Failed to start new chat. Please refresh the page manually.');
            });
        }
    },

    handleHistory() {
        // Placeholder for future history feature
        Notifications.info('Chat history feature coming soon!');
    },

    handleSettings() {
        // Placeholder for future settings feature
        Notifications.info('Settings feature coming soon!');
    }
};
