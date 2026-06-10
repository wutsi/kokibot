/**
 * Settings Page Controller
 * Manages settings page tabs and agent info loading
 */
const Settings = {
    agentName: null,
    tabs: [],
    tabContents: [],
    agentNameElement: null,
    agentDescriptionElement: null,
    chatButton: null,
    backToChatLink: null,
    instructionsEditBtn: null,
    instructionsSaveBtn: null,
    instructionsCancelBtn: null,
    instructionsOriginalContent: null,
    isEditingInstructions: false,
    heartbeatEditBtn: null,
    heartbeatSaveBtn: null,
    heartbeatCancelBtn: null,
    heartbeatOriginalContent: null,
    isEditingHeartbeat: false,

    init(agentName) {
        this.agentName = agentName;
        this.setupElements();
        this.setupTabLoaders();
        this.setupEventListeners();
        this.loadAgentInfo();
        this.loadActiveTab();
    },

    setupElements() {
        this.tabs = Array.from(document.querySelectorAll('.settings-tab'));
        this.tabContents = Array.from(document.querySelectorAll('.settings-tab-content'));
        this.agentNameElement = document.getElementById('agent-name');
        this.agentDescriptionElement = document.getElementById('agent-description');
        this.chatButton = document.getElementById('chat-btn');
        this.backToChatLink = document.querySelector('.back-to-chat-btn');
        this.instructionsEditBtn = document.getElementById('instructions-edit-btn');
        this.instructionsSaveBtn = document.getElementById('instructions-save-btn');
        this.instructionsCancelBtn = document.getElementById('instructions-cancel-btn');
        this.heartbeatEditBtn = document.getElementById('heartbeat-edit-btn');
        this.heartbeatSaveBtn = document.getElementById('heartbeat-save-btn');
        this.heartbeatCancelBtn = document.getElementById('heartbeat-cancel-btn');
    },

    setupEventListeners() {
        // Tab click handlers
        this.tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.dataset.tab;
                this.switchTab(tabName);
                this.onTabActivated(tabName);
            });
        });

        // Chat button - navigate back to chat with agent parameter
        if (this.chatButton) {
            this.chatButton.addEventListener('click', () => {
                this.navigateToChat();
            });
        }

        // Back to chat link - add agent parameter
        if (this.backToChatLink) {
            this.backToChatLink.addEventListener('click', (e) => {
                e.preventDefault();
                this.navigateToChat();
            });
        }

        // Instructions edit buttons
        if (this.instructionsEditBtn) {
            this.instructionsEditBtn.addEventListener('click', () => {
                this.enterInstructionsEditMode();
            });
        }

        if (this.instructionsSaveBtn) {
            this.instructionsSaveBtn.addEventListener('click', () => {
                this.saveInstructions();
            });
        }

        if (this.instructionsCancelBtn) {
            this.instructionsCancelBtn.addEventListener('click', () => {
                this.cancelInstructionsEdit();
            });
        }

        // Heartbeat edit buttons
        if (this.heartbeatEditBtn) {
            this.heartbeatEditBtn.addEventListener('click', () => {
                this.enterHeartbeatEditMode();
            });
        }

        if (this.heartbeatSaveBtn) {
            this.heartbeatSaveBtn.addEventListener('click', () => {
                this.saveHeartbeat();
            });
        }

        if (this.heartbeatCancelBtn) {
            this.heartbeatCancelBtn.addEventListener('click', () => {
                this.cancelHeartbeatEdit();
            });
        }
    },

    setupTabLoaders() {
        this.loadedTabs = new Set();
    },

    onTabActivated(tabName, forceReload = false) {
        // Load content for tabs that need data fetching
        if (!forceReload && this.loadedTabs.has(tabName)) {
            return; // Already loaded
        }

        switch (tabName) {
            case 'general':
                this.loadGeneral();
                this.loadedTabs.add(tabName);
                break;
            case 'instructions':
                this.loadInstructions();
                this.loadedTabs.add(tabName);
                break;
            case 'heartbeat':
                this.loadHeartbeat();
                this.loadedTabs.add(tabName);
                break;
            case 'skills':
                this.loadSkills();
                this.loadedTabs.add(tabName);
                break;
        }
    },

    navigateToChat() {
        const agentParam = this.agentName ? `?agent=${this.agentName}` : '';
        window.location.href = `/${agentParam}`;
    },

    switchTab(tabName) {
        // Update tabs
        this.tabs.forEach(tab => {
            if (tab.dataset.tab === tabName) {
                tab.classList.add('active');
            } else {
                tab.classList.remove('active');
            }
        });

        // Update tab contents
        this.tabContents.forEach(content => {
            if (content.dataset.content === tabName) {
                content.classList.add('active');
            } else {
                content.classList.remove('active');
            }
        });

        // Save active tab to localStorage
        localStorage.setItem('settings_active_tab', tabName);
    },

    loadActiveTab() {
        // Load previously active tab from localStorage
        const savedTab = localStorage.getItem('settings_active_tab');
        const activeTab = savedTab || 'general';

        if (savedTab) {
            this.switchTab(savedTab);
        }

        // Trigger loading for the active tab with force reload
        this.onTabActivated(activeTab, true);
    },

    async loadAgentInfo() {
        if (!this.agentName) {
            return;
        }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000);

            const response = await fetch(`/assistants/${this.agentName}`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load agent info (${response.status})`);
            }

            const data = await response.json();

            // Update UI
            if (this.agentNameElement) {
                this.agentNameElement.textContent = this.formatAgentName(data.name);
            }

            if (this.agentDescriptionElement && data.description) {
                this.agentDescriptionElement.textContent = data.description;
            }
        } catch (error) {
            console.error('Error loading agent info:', error);

            // Don't show notification for timeouts (less critical)
            if (error.name !== 'AbortError') {
                Notifications.warning(
                    'Failed to load agent information',
                    { duration: 3000 }
                );
            }
        }
    },

    formatAgentName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    },

    async loadGeneral() {
        if (!this.agentName) {
            this.showGeneralError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('general-content');
        if (!contentElement) {
            return;
        }

        // Show loading state
        contentElement.innerHTML = `
            <div class="general-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading general information...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            // Fetch both agent info and LLM info in parallel
            const [agentResponse, llmResponse] = await Promise.all([
                fetch(`/assistants/${this.agentName}`, { signal: controller.signal }),
                fetch(`/assistants/${this.agentName}/llm`, { signal: controller.signal })
            ]);

            clearTimeout(timeoutId);

            if (!agentResponse.ok || !llmResponse.ok) {
                throw new Error('Failed to load general information');
            }

            const agentData = await agentResponse.json();
            const llmData = await llmResponse.json();

            // Display general info
            this.displayGeneralInfo(agentData, llmData);
        } catch (error) {
            console.error('Error loading general information:', error);

            if (error.name === 'AbortError') {
                this.showGeneralError('Request timed out. Please try again.');
            } else {
                this.showGeneralError('Failed to load general information. Please try again.');
            }

            Notifications.error('Failed to load general information', {
                duration: 5000
            });
        }
    },

    displayGeneralInfo(agentData, llmData) {
        const contentElement = document.getElementById('general-content');
        if (!contentElement) return;

        const workspace = agentData.workspaceDirectory || 'Unknown';
        const llmName = llmData.name || 'Unknown';
        const llmModel = llmData.model || 'Unknown';
        const maxContextWindow = this.formatContextLength(llmData.maxContextWindow || 0);
        const balance = llmData.availableBalance;

        let balanceHtml = '';
        if (balance) {
            balanceHtml = `
                <div class="general-item">
                    <p class="general-item-label">Available Balance</p>
                    <p class="general-item-value">${this.escapeHtml(balance.text)}</p>
                </div>
            `;
        }

        contentElement.innerHTML = `
            <div class="general-info">
                <div class="general-section">
                    <h3 class="general-section-title">Workspace</h3>
                    <div class="general-item">
                        <p class="general-item-label">Workspace Directory</p>
                        <p class="general-item-value">${workspace}</p>
                    </div>
                </div>
                <div class="general-section">
                    <h3 class="general-section-title">Language Model</h3>
                    <div class="general-item">
                        <p class="general-item-label">Provider</p>
                        <div class="general-llm-provider">
                            <img src="/assets/${llmName}.png" alt="${llmName}" class="general-llm-icon"
                                 onerror="this.style.display='none'">
                            <p class="general-llm-name">${this.formatLLMName(llmName)}</p>
                        </div>
                    </div>
                    <div class="general-item">
                        <p class="general-item-label">Model</p>
                        <p class="general-item-value">${llmModel}</p>
                    </div>
                    <div class="general-item">
                        <p class="general-item-label">Max Context Window</p>
                        <p class="general-item-value">${maxContextWindow}</p>
                    </div>
                    ${balanceHtml}
                </div>
            </div>
        `;
    },

    showGeneralError(message) {
        const contentElement = document.getElementById('general-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="general-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading General Information</h3>
                <p>${message}</p>
            </div>
        `;
    },

    async loadInstructions() {
        if (!this.agentName) {
            this.showInstructionsError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('instructions-content');
        if (!contentElement) {
            return;
        }

        // Show loading state
        contentElement.innerHTML = `
            <div class="instructions-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading instructions...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/assistant.md`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load instructions (${response.status})`);
            }

            const data = await response.json();
            const textContent = data.content || '';

            // Store original content
            this.instructionsOriginalContent = textContent;

            if (!textContent.trim()) {
                this.showInstructionsEmpty();
                this.showInstructionsEditButton();
                return;
            }

            // Display as plain text
            const pre = document.createElement('pre');
            pre.className = 'instructions-text';
            pre.textContent = textContent;

            contentElement.innerHTML = '';
            contentElement.appendChild(pre);

            // Show edit button
            this.showInstructionsEditButton();
        } catch (error) {
            console.error('Error loading instructions:', error);

            if (error.name === 'AbortError') {
                this.showInstructionsError('Request timed out. Please try again.');
            } else {
                this.showInstructionsError('Failed to load instructions. Please try again.');
            }

            Notifications.error('Failed to load assistant instructions', {
                duration: 5000
            });
        }
    },

    showInstructionsEmpty() {
        const contentElement = document.getElementById('instructions-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="instructions-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
                </svg>
                <h3>No Instructions</h3>
                <p>This assistant has no system instructions configured</p>
            </div>
        `;
    },

    showInstructionsError(message) {
        const contentElement = document.getElementById('instructions-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="instructions-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Instructions</h3>
                <p>${message}</p>
            </div>
        `;
    },

    showInstructionsEditButton() {
        if (this.instructionsEditBtn) {
            this.instructionsEditBtn.style.display = 'flex';
        }
        if (this.instructionsSaveBtn) {
            this.instructionsSaveBtn.style.display = 'none';
        }
        if (this.instructionsCancelBtn) {
            this.instructionsCancelBtn.style.display = 'none';
        }
    },

    showInstructionsSaveButtons() {
        if (this.instructionsEditBtn) {
            this.instructionsEditBtn.style.display = 'none';
        }
        if (this.instructionsSaveBtn) {
            this.instructionsSaveBtn.style.display = 'flex';
        }
        if (this.instructionsCancelBtn) {
            this.instructionsCancelBtn.style.display = 'flex';
        }
    },

    enterInstructionsEditMode() {
        const contentElement = document.getElementById('instructions-content');
        if (!contentElement) return;

        this.isEditingInstructions = true;

        // Create textarea
        const textarea = document.createElement('textarea');
        textarea.className = 'instructions-editor';
        textarea.value = this.instructionsOriginalContent || '';
        textarea.id = 'instructions-editor-textarea';

        contentElement.innerHTML = '';
        contentElement.appendChild(textarea);

        // Show save/cancel buttons
        this.showInstructionsSaveButtons();

        // Focus textarea
        textarea.focus();
    },

    async saveInstructions() {
        if (!this.agentName) {
            Notifications.error('No agent selected');
            return;
        }

        const textarea = document.getElementById('instructions-editor-textarea');
        if (!textarea) return;

        const content = textarea.value;

        // Disable buttons during save
        if (this.instructionsSaveBtn) this.instructionsSaveBtn.disabled = true;
        if (this.instructionsCancelBtn) this.instructionsCancelBtn.disabled = true;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/assistant.md`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ content }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to save instructions (${response.status})`);
            }

            // Update stored content
            this.instructionsOriginalContent = content;

            // Exit edit mode
            this.exitInstructionsEditMode();

            Notifications.success('Instructions saved successfully', {
                duration: 3000
            });
        } catch (error) {
            console.error('Error saving instructions:', error);

            if (error.name === 'AbortError') {
                Notifications.error('Save request timed out. Please try again.');
            } else {
                Notifications.error('Failed to save instructions. Please try again.');
            }
        } finally {
            // Re-enable buttons
            if (this.instructionsSaveBtn) this.instructionsSaveBtn.disabled = false;
            if (this.instructionsCancelBtn) this.instructionsCancelBtn.disabled = false;
        }
    },

    cancelInstructionsEdit() {
        this.exitInstructionsEditMode();
    },

    exitInstructionsEditMode() {
        this.isEditingInstructions = false;

        const contentElement = document.getElementById('instructions-content');
        if (!contentElement) return;

        // Restore read-only view
        if (!this.instructionsOriginalContent || !this.instructionsOriginalContent.trim()) {
            this.showInstructionsEmpty();
        } else {
            const pre = document.createElement('pre');
            pre.className = 'instructions-text';
            pre.textContent = this.instructionsOriginalContent;

            contentElement.innerHTML = '';
            contentElement.appendChild(pre);
        }

        // Show edit button
        this.showInstructionsEditButton();
    },

    async loadHeartbeat() {
        if (!this.agentName) {
            this.showHeartbeatError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) {
            return;
        }

        // Show loading state
        contentElement.innerHTML = `
            <div class="heartbeat-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading heartbeat instructions...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/heartbeat.md`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load heartbeat instructions (${response.status})`);
            }

            const data = await response.json();
            const textContent = data.content || '';

            // Store original content
            this.heartbeatOriginalContent = textContent;

            if (!textContent.trim()) {
                this.showHeartbeatEmpty();
                this.showHeartbeatEditButton();
                return;
            }

            // Display as plain text
            const pre = document.createElement('pre');
            pre.className = 'heartbeat-text';
            pre.textContent = textContent;

            contentElement.innerHTML = '';
            contentElement.appendChild(pre);

            // Show edit button
            this.showHeartbeatEditButton();
        } catch (error) {
            console.error('Error loading heartbeat instructions:', error);

            if (error.name === 'AbortError') {
                this.showHeartbeatError('Request timed out. Please try again.');
            } else {
                this.showHeartbeatError('Failed to load heartbeat instructions. Please try again.');
            }

            Notifications.error('Failed to load heartbeat instructions', {
                duration: 5000
            });
        }
    },

    showHeartbeatEmpty() {
        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="heartbeat-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                </svg>
                <h3>No Heartbeat Instructions</h3>
                <p>This assistant has no heartbeat instructions configured</p>
            </div>
        `;
    },

    showHeartbeatError(message) {
        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="heartbeat-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Heartbeat Instructions</h3>
                <p>${message}</p>
            </div>
        `;
    },

    showHeartbeatEditButton() {
        if (this.heartbeatEditBtn) {
            this.heartbeatEditBtn.style.display = 'flex';
        }
        if (this.heartbeatSaveBtn) {
            this.heartbeatSaveBtn.style.display = 'none';
        }
        if (this.heartbeatCancelBtn) {
            this.heartbeatCancelBtn.style.display = 'none';
        }
    },

    showHeartbeatSaveButtons() {
        if (this.heartbeatEditBtn) {
            this.heartbeatEditBtn.style.display = 'none';
        }
        if (this.heartbeatSaveBtn) {
            this.heartbeatSaveBtn.style.display = 'flex';
        }
        if (this.heartbeatCancelBtn) {
            this.heartbeatCancelBtn.style.display = 'flex';
        }
    },

    enterHeartbeatEditMode() {
        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) return;

        this.isEditingHeartbeat = true;

        // Create textarea
        const textarea = document.createElement('textarea');
        textarea.className = 'heartbeat-editor';
        textarea.value = this.heartbeatOriginalContent || '';
        textarea.id = 'heartbeat-editor-textarea';

        contentElement.innerHTML = '';
        contentElement.appendChild(textarea);

        // Show save/cancel buttons
        this.showHeartbeatSaveButtons();

        // Focus textarea
        textarea.focus();
    },

    async saveHeartbeat() {
        if (!this.agentName) {
            Notifications.error('No agent selected');
            return;
        }

        const textarea = document.getElementById('heartbeat-editor-textarea');
        if (!textarea) return;

        const content = textarea.value;

        // Disable buttons during save
        if (this.heartbeatSaveBtn) this.heartbeatSaveBtn.disabled = true;
        if (this.heartbeatCancelBtn) this.heartbeatCancelBtn.disabled = true;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/heartbeat.md`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ content }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to save heartbeat instructions (${response.status})`);
            }

            // Update stored content
            this.heartbeatOriginalContent = content;

            // Exit edit mode
            this.exitHeartbeatEditMode();

            Notifications.success('Heartbeat instructions saved successfully', {
                duration: 3000
            });
        } catch (error) {
            console.error('Error saving heartbeat instructions:', error);

            if (error.name === 'AbortError') {
                Notifications.error('Save request timed out. Please try again.');
            } else {
                Notifications.error('Failed to save heartbeat instructions. Please try again.');
            }
        } finally {
            // Re-enable buttons
            if (this.heartbeatSaveBtn) this.heartbeatSaveBtn.disabled = false;
            if (this.heartbeatCancelBtn) this.heartbeatCancelBtn.disabled = false;
        }
    },

    cancelHeartbeatEdit() {
        this.exitHeartbeatEditMode();
    },

    exitHeartbeatEditMode() {
        this.isEditingHeartbeat = false;

        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) return;

        // Restore read-only view
        if (!this.heartbeatOriginalContent || !this.heartbeatOriginalContent.trim()) {
            this.showHeartbeatEmpty();
        } else {
            const pre = document.createElement('pre');
            pre.className = 'heartbeat-text';
            pre.textContent = this.heartbeatOriginalContent;

            contentElement.innerHTML = '';
            contentElement.appendChild(pre);
        }

        // Show edit button
        this.showHeartbeatEditButton();
    },

    async loadLLM() {
        if (!this.agentName) {
            this.showLLMError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('llm-content');
        if (!contentElement) {
            return;
        }

        // Show loading state
        contentElement.innerHTML = `
            <div class="llm-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading LLM configuration...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/llm`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load LLM configuration (${response.status})`);
            }

            const data = await response.json();

            // Display LLM info
            this.displayLLMInfo(data);
        } catch (error) {
            console.error('Error loading LLM configuration:', error);

            if (error.name === 'AbortError') {
                this.showLLMError('Request timed out. Please try again.');
            } else {
                this.showLLMError('Failed to load LLM configuration. Please try again.');
            }

            Notifications.error('Failed to load LLM configuration', {
                duration: 5000
            });
        }
    },

    displayLLMInfo(data) {
        const contentElement = document.getElementById('llm-content');
        if (!contentElement) return;

        const llmName = data.name || 'Unknown';
        const model = data.model || 'Unknown';
        const maxContextLength = this.formatContextLength(data.maxContextWindow || 0);

        contentElement.innerHTML = `
            <div class="llm-info">
                <div class="llm-provider">
                    <img src="/assets/${llmName}.png" alt="${llmName}" class="llm-provider-icon"
                         onerror="this.style.display='none'">
                    <div class="llm-provider-info">
                        <h3 class="llm-provider-name">${this.formatLLMName(llmName)}</h3>
                        <p class="llm-provider-model">${model}</p>
                    </div>
                </div>
                <div class="llm-details">
                    <div class="llm-detail-item">
                        <p class="llm-detail-label">Max Context Window</p>
                        <p class="llm-detail-value">${maxContextLength}</p>
                    </div>
                </div>
            </div>
        `;
    },

    formatLLMName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    },

    formatContextLength(tokens) {
        if (tokens >= 1000000) {
            return `${(tokens / 1000000).toFixed(1)}M tokens`;
        } else if (tokens >= 1000) {
            return `${Math.round(tokens / 1000)}K tokens`;
        } else {
            return `${tokens} tokens`;
        }
    },

    showLLMError(message) {
        const contentElement = document.getElementById('llm-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="llm-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading LLM Configuration</h3>
                <p>${message}</p>
            </div>
        `;
    },

    async loadSkills() {
        if (!this.agentName) {
            this.showSkillsError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('skills-content');
        if (!contentElement) {
            return;
        }

        // Show loading state
        contentElement.innerHTML = `
            <div class="skills-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading skills...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/skills`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load skills (${response.status})`);
            }

            const skills = await response.json();

            if (!skills || skills.length === 0) {
                this.showSkillsEmpty();
                return;
            }

            // Display skills
            this.displaySkills(skills);
        } catch (error) {
            console.error('Error loading skills:', error);

            if (error.name === 'AbortError') {
                this.showSkillsError('Request timed out. Please try again.');
            } else {
                this.showSkillsError('Failed to load skills. Please try again.');
            }

            Notifications.error('Failed to load skills', {
                duration: 5000
            });
        }
    },

    displaySkills(skills) {
        const contentElement = document.getElementById('skills-content');
        if (!contentElement) return;

        const skillsHtml = skills.map(skill => `
            <div class="skill-item">
                <h3 class="skill-name">${this.escapeHtml(skill.name)}</h3>
                <p class="skill-description">${this.escapeHtml(skill.description || 'No description available')}</p>
            </div>
        `).join('');

        contentElement.innerHTML = `
            <div class="skills-list">
                ${skillsHtml}
            </div>
        `;
    },

    showSkillsEmpty() {
        const contentElement = document.getElementById('skills-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="skills-empty">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"/>
                </svg>
                <h3>No Skills Available</h3>
                <p>This assistant has no skills configured</p>
            </div>
        `;
    },

    showSkillsError(message) {
        const contentElement = document.getElementById('skills-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="skills-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Skills</h3>
                <p>${message}</p>
            </div>
        `;
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
