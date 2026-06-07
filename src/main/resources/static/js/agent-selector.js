/**
 * Agent Selector UI
 * Handles displaying and switching between available agents
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
            const response = await fetch('/assistants');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            this.agents = await response.json();
            this.renderAgents();
        } catch (error) {
            console.error('Error loading agents:', error);
            this.agentListContainer.innerHTML = `
                <div class="agent-list-error">
                    Failed to load agents. Please try again.
                </div>
            `;
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

        // Update context gauge before reloading
        if (typeof ContextGauge !== 'undefined') {
            ContextGauge.setAgent(agentName);
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
