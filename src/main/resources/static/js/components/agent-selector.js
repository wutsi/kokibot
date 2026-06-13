/**
 * Agent Selector Component
 * Modal interface for viewing and switching between available agents
 * Loads agent list from API and handles agent transitions
 */
const AgentSelector = {
    modal: null,
    agentListContainer: null,
    currentAgent: null,
    agents: [],

    init(currentAgent) {
        this.currentAgent = currentAgent;
        this.setupElements();
        this.setupEventListeners();
    },

    setupElements() {
        this.modal = document.getElementById('agent-selector-modal');
        this.agentListContainer = document.getElementById('agent-list');
        this.selectorButton = document.getElementById('agent-selector-btn');
        this.closeButton = document.getElementById('close-modal-btn');
    },

    setupEventListeners() {
        // Open modal
        this.selectorButton.addEventListener('click', () => {
            this.openModal();
        });

        // Close modal
        this.closeButton.addEventListener('click', () => {
            this.closeModal();
        });

        // Close modal when clicking outside
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.closeModal();
            }
        });

        // Close modal on Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.modal.classList.contains('show')) {
                this.closeModal();
            }
        });

        // Auto-open when the current agent is not found
        document.addEventListener('agent-not-found', () => {
            this.openModal();
        });
    },

    async openModal() {
        this.modal.classList.add('show');
        await this.loadAgents();
    },

    closeModal() {
        this.modal.classList.remove('show');
    },

    async loadAgents() {
        this.agentListContainer.innerHTML = '<div class="agent-list-loading">Loading agents...</div>';

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000); // 10s timeout

            const response = await fetch('/assistants?channel-id=websocket', {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load agents (${response.status})`);
            }

            this.agents = await response.json();
            this.renderAgents();
        } catch (error) {
            console.error('Error loading agents:', error);

            const errorMessage = error.name === 'AbortError'
                ? 'Request timed out. Please try again.'
                : `Failed to load agents: ${error.message}`;

            this.agentListContainer.innerHTML = `
                <div class="agent-list-error">
                    ${errorMessage}
                    <button class="notification-btn notification-btn-primary" style="margin-top: 12px;">
                        Retry
                    </button>
                </div>
            `;

            // Add retry handler
            const retryBtn = this.agentListContainer.querySelector('button');
            if (retryBtn) {
                retryBtn.addEventListener('click', () => this.loadAgents());
            }

            // Also show toast notification
            Notifications.error(errorMessage, {
                retry: {
                    label: 'Retry',
                    callback: () => this.loadAgents()
                }
            });
        }
    },

    renderAgents() {
        if (this.agents.length === 0) {
            this.agentListContainer.innerHTML = `
                <div class="agent-list-loading">
                    No agents available
                </div>
            `;
            return;
        }

        this.agentListContainer.innerHTML = '';

        this.agents.forEach(agentName => {
            const agentItem = document.createElement('div');
            agentItem.className = 'agent-item';

            if (agentName === this.currentAgent) {
                agentItem.classList.add('current');
            }

            const nameDiv = document.createElement('div');
            nameDiv.className = 'agent-item-name';
            nameDiv.textContent = this.formatAgentName(agentName);

            agentItem.appendChild(nameDiv);

            // Add "Current" badge for the active agent
            if (agentName === this.currentAgent) {
                const badge = document.createElement('div');
                badge.className = 'agent-item-badge';
                badge.textContent = 'Current';
                agentItem.appendChild(badge);
            }

            // Click handler to switch agent
            agentItem.addEventListener('click', () => {
                this.switchAgent(agentName);
            });

            this.agentListContainer.appendChild(agentItem);
        });
    },

    switchAgent(agentName) {
        if (agentName === this.currentAgent) {
            this.closeModal();
            return;
        }

        // Clear uploaded files before reloading
        if (typeof FileUpload !== 'undefined') {
            FileUpload.setAgent(agentName);
        }

        // Update URL and reload
        const url = new URL(window.location);
        url.searchParams.set('agent', agentName);
        window.location.href = url.toString();
    },

    formatAgentName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }
};
