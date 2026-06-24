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
    heartbeatEditBtn: null,
    heartbeatSaveBtn: null,
    heartbeatCancelBtn: null,
    heartbeatOriginalContent: null,
    isEditingHeartbeat: false,
    generalInstructionsContent: null,

    init(agentName) {
        this.agentName = agentName;
        this.setupElements();
        this.setupTabLoaders();
        this.setupEventListeners();
        this.loadAgentInfo();
        this.loadActiveTab();
    },

    setupElements() {
        this.tabs = Array.from(document.querySelectorAll('.settings-nav-item[data-tab]'));
        this.tabContents = Array.from(document.querySelectorAll('.settings-tab-content'));
        this.agentNameElement = document.getElementById('agent-name');
        this.agentDescriptionElement = document.getElementById('agent-description');
        this.chatButton = document.getElementById('chat-btn');
        this.backToChatLink = document.querySelector('.back-to-chat-btn');
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
            case 'heartbeat':
                this.loadHeartbeat();
                this.loadedTabs.add(tabName);
                break;
            case 'skills':
                this.loadSkills();
                this.loadedTabs.add(tabName);
                break;
            case 'llm':
                this.loadLLM();
                this.loadedTabs.add(tabName);
                break;
            case 'channels':
                this.loadChannels();
                this.loadedTabs.add(tabName);
                break;
            case 'marketplaces':
                this.loadMarketplaces();
                this.loadedTabs.add(tabName);
                break;
            case 'connectors':
                this.loadMcp();
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

            const agentResponse = await fetch(`/assistants/${this.agentName}`, { signal: controller.signal });

            clearTimeout(timeoutId);

            if (!agentResponse.ok) {
                throw new Error('Failed to load general information');
            }

            const agentData = await agentResponse.json();

            // Display general info
            this.displayGeneralInfo(agentData);
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

    displayGeneralInfo(agentData) {
        const contentElement = document.getElementById('general-content');
        if (!contentElement) return;

        const name = this.formatAgentName(agentData.name || '');
        const description = agentData.description || '';
        const instructions = agentData.instructions || '';
        const iconUrl = `/assistants/${this.escapeHtml(this.agentName)}/icon.png`;

        this.generalInstructionsContent = instructions;

        const instructionsBodyHtml = instructions.trim()
            ? `<div class="instructions-text markdown-body" id="general-instructions-body">${new MarkdownRenderer().render(instructions)}</div>`
            : `<p class="general-instructions-empty" id="general-instructions-body">No instructions configured</p>`;

        contentElement.innerHTML = `
            <div class="general-info">
                <div class="general-section">
                    <div class="general-agent-header">
                        <div class="general-agent-icon-container" id="general-agent-icon-container" title="Click to upload PNG icon">
                            <img src="${iconUrl}" alt="${name}" class="general-agent-icon" id="general-agent-icon"
                                 onerror="this.style.display='none'">
                            <div class="general-agent-icon-overlay">
                                <svg fill="currentColor" height="22" viewBox="0 0 24 24" width="22">
                                    <path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zm0 8c-1.65 0-3-1.35-3-3s1.35-3 3-3 3 1.35 3 3-1.35 3-3 3zM20 4h-3.17L15 2H9L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z"/>
                                </svg>
                            </div>
                            <input type="file" accept=".png,image/png" id="general-icon-upload-input" style="display:none;">
                        </div>
                        <div class="general-agent-details">
                            <h3 class="general-agent-name">${name}</h3>
                            ${description ? `<p class="general-agent-description">${this.escapeHtml(description)}</p>` : ''}
                        </div>
                    </div>
                </div>
                <div class="general-section">
                    <div class="general-instructions-header">
                        <h3 class="general-section-title">Instructions</h3>
                        <div class="general-instructions-actions">
                            <button class="settings-action-btn settings-action-btn-secondary" id="general-instructions-edit-btn">
                                <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                                </svg>
                                Edit
                            </button>
                            <button class="settings-action-btn settings-action-btn-primary" id="general-instructions-save-btn" style="display:none;">
                                <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                    <path d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"/>
                                </svg>
                                Save
                            </button>
                            <button class="settings-action-btn settings-action-btn-secondary" id="general-instructions-cancel-btn" style="display:none;">
                                <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                    <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
                                </svg>
                                Cancel
                            </button>
                        </div>
                    </div>
                    ${instructionsBodyHtml}
                </div>
            </div>
        `;

        document.getElementById('general-instructions-edit-btn')?.addEventListener('click', () => {
            this.enterGeneralInstructionsEditMode();
        });
        document.getElementById('general-instructions-save-btn')?.addEventListener('click', () => {
            this.saveGeneralInstructions();
        });
        document.getElementById('general-instructions-cancel-btn')?.addEventListener('click', () => {
            this.exitGeneralInstructionsEditMode();
        });

        const iconContainer = document.getElementById('general-agent-icon-container');
        const iconInput = document.getElementById('general-icon-upload-input');
        if (iconContainer && iconInput) {
            iconContainer.addEventListener('click', () => iconInput.click());
            iconInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file) this.uploadIcon(file);
            });
        }
    },

    enterGeneralInstructionsEditMode() {
        const body = document.getElementById('general-instructions-body');
        if (!body) return;

        const textarea = document.createElement('textarea');
        textarea.className = 'instructions-editor';
        textarea.value = this.generalInstructionsContent || '';
        textarea.id = 'general-instructions-editor-textarea';
        body.replaceWith(textarea);
        textarea.focus();

        document.getElementById('general-instructions-edit-btn').style.display = 'none';
        document.getElementById('general-instructions-save-btn').style.display = 'flex';
        document.getElementById('general-instructions-cancel-btn').style.display = 'flex';
    },

    async saveGeneralInstructions() {
        if (!this.agentName) {
            Notifications.error('No agent selected');
            return;
        }

        const textarea = document.getElementById('general-instructions-editor-textarea');
        if (!textarea) return;

        const content = textarea.value;
        const saveBtn = document.getElementById('general-instructions-save-btn');
        const cancelBtn = document.getElementById('general-instructions-cancel-btn');

        if (saveBtn) saveBtn.disabled = true;
        if (cancelBtn) cancelBtn.disabled = true;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/assistant.md`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to save instructions (${response.status})`);
            }

            this.generalInstructionsContent = content;
            this.exitGeneralInstructionsEditMode();
            Notifications.success('Instructions saved successfully', { duration: 3000 });
        } catch (error) {
            console.error('Error saving instructions:', error);
            if (error.name === 'AbortError') {
                Notifications.error('Save request timed out. Please try again.');
            } else {
                Notifications.error('Failed to save instructions. Please try again.');
            }
        } finally {
            if (saveBtn) saveBtn.disabled = false;
            if (cancelBtn) cancelBtn.disabled = false;
        }
    },

    exitGeneralInstructionsEditMode() {
        const textarea = document.getElementById('general-instructions-editor-textarea');
        if (!textarea) return;

        const div = document.createElement(this.generalInstructionsContent?.trim() ? 'div' : 'p');
        div.id = 'general-instructions-body';

        if (this.generalInstructionsContent?.trim()) {
            div.className = 'instructions-text markdown-body';
            div.innerHTML = new MarkdownRenderer().render(this.generalInstructionsContent);
        } else {
            div.className = 'general-instructions-empty';
            div.textContent = 'No instructions configured';
        }

        textarea.replaceWith(div);

        document.getElementById('general-instructions-edit-btn').style.display = 'flex';
        document.getElementById('general-instructions-save-btn').style.display = 'none';
        document.getElementById('general-instructions-cancel-btn').style.display = 'none';
    },

    async uploadIcon(file) {
        if (file.type !== 'image/png') {
            Notifications.error('Only PNG files are accepted');
            return;
        }

        const formData = new FormData();
        formData.append('file', file, 'icon.png');

        try {
            const response = await fetch(`/assistants/${this.agentName}/icon.png`, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error(`Upload failed (${response.status})`);
            }

            const img = document.getElementById('general-agent-icon');
            if (img) {
                img.style.display = '';
                img.src = `/assistants/${this.agentName}/icon.png?t=${Date.now()}`;
            }

            Notifications.success('Icon uploaded successfully', { duration: 3000 });
        } catch (error) {
            console.error('Error uploading icon:', error);
            Notifications.error('Failed to upload icon. Please try again.');
        }
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

            const div = document.createElement('div');
            div.className = 'heartbeat-text markdown-body';
            div.innerHTML = new MarkdownRenderer().render(textContent);

            contentElement.innerHTML = '';
            contentElement.appendChild(div);

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
            const div = document.createElement('div');
            div.className = 'heartbeat-text markdown-body';
            div.innerHTML = new MarkdownRenderer().render(this.heartbeatOriginalContent);

            contentElement.innerHTML = '';
            contentElement.appendChild(div);
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
        const balance = data.availableBalance;

        const balanceHtml = balance ? `
            <div class="llm-detail-item">
                <p class="llm-detail-label">Available Balance</p>
                <p class="llm-detail-value">${this.escapeHtml(balance.text)}</p>
            </div>
        ` : '';

        contentElement.innerHTML = `
            <div class="llm-info">
                <div class="llm-provider">
                    <img src="/assets/llm/${llmName}.png" alt="${llmName}" class="llm-provider-icon"
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
                    ${balanceHtml}
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

        const listHtml = skills.map((skill, i) => `
            <button class="skill-list-item${i === 0 ? ' active' : ''}" data-skill="${this.escapeHtml(skill.name)}">
                ${this.escapeHtml(skill.name)}
            </button>
        `).join('');

        contentElement.innerHTML = `
            <div class="skills-layout">
                <div class="skills-list-panel">${listHtml}</div>
                <div class="skill-detail-panel" id="skill-detail-panel">
                    <div class="skill-detail-placeholder">Select a skill to view details</div>
                </div>
            </div>
        `;

        contentElement.querySelectorAll('.skill-list-item').forEach(btn => {
            btn.addEventListener('click', () => {
                contentElement.querySelectorAll('.skill-list-item').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const skill = skills.find(s => s.name === btn.dataset.skill);
                this.loadSkillContent(skill);
            });
        });

        if (skills.length > 0) {
            this.loadSkillContent(skills[0]);
        }
    },

    async loadSkillContent(skill) {
        const panel = document.getElementById('skill-detail-panel');
        if (!panel) return;

        panel.innerHTML = `
            <div class="skill-detail-loading">
                <svg class="loading-spinner" fill="currentColor" height="32" viewBox="0 0 24 24" width="32">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
            </div>
        `;

        try {
            const response = await fetch(`/assistants/${this.agentName}/skills/skill.md?skill=${encodeURIComponent(skill.name)}`);
            const content = response.ok ? (await response.json()).content || '' : '';

            const instructionsHtml = content.trim()
                ? `<div class="skill-detail-instructions">
                       <h3 class="skill-detail-instructions-title">Instructions</h3>
                       <div class="skill-detail-content markdown-body">${new MarkdownRenderer().render(content)}</div>
                   </div>`
                : '';

            panel.innerHTML = `
                <div class="skill-detail">
                    <h2 class="skill-detail-name">${this.escapeHtml(skill.name)}</h2>
                    ${skill.description ? `<p class="skill-detail-description">${this.escapeHtml(skill.description)}</p>` : ''}
                    ${instructionsHtml}
                </div>
            `;
        } catch (error) {
            panel.innerHTML = `<div class="skill-detail-placeholder">Failed to load skill content</div>`;
        }
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

    async loadMcp() {
        if (!this.agentName) {
            this.showMcpError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('mcp-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="mcp-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading MCP servers...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/mcps`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load MCP servers (${response.status})`);
            }

            const mcps = await response.json();

            if (!mcps || mcps.length === 0) {
                this.showMcpEmpty();
                return;
            }

            this.displayMcp(mcps);
        } catch (error) {
            console.error('Error loading MCP servers:', error);

            if (error.name === 'AbortError') {
                this.showMcpError('Request timed out. Please try again.');
            } else {
                this.showMcpError('Failed to load MCP servers. Please try again.');
            }

            Notifications.error('Failed to load MCP servers', { duration: 5000 });
        }
    },

    displayMcp(mcps) {
        const contentElement = document.getElementById('mcp-content');
        if (!contentElement) return;

        const mcpsHtml = mcps.map(mcp => {
            const iconHtml = mcp.icon
                ? `<img src="${this.escapeHtml(mcp.icon)}" alt="${this.escapeHtml(mcp.name)}" class="channel-icon" onerror="this.style.display='none'">`
                : '';
            return `
            <div class="channel-item">
                ${iconHtml}
                <div class="channel-info">
                    <span class="channel-name">${this.escapeHtml(mcp.name)}</span>
                    ${mcp.description ? `<span class="channel-source">${this.escapeHtml(mcp.description)}</span>` : ''}
                </div>
            </div>
        `;
        }).join('');

        contentElement.innerHTML = `<div class="channels-list">${mcpsHtml}</div>`;
    },

    showMcpEmpty() {
        const contentElement = document.getElementById('mcp-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="skills-empty">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M13 2.05V4.05C17.39 4.59 20.5 8.58 19.96 12.97C19.5 16.61 16.64 19.5 13 19.93V21.93C18.5 21.38 22.5 16.5 21.95 11C21.5 6.25 17.73 2.5 13 2.05M11 2.06C9.05 2.25 7.19 3 5.67 4.26L7.1 5.74C8.22 4.84 9.57 4.26 11 4.06V2.06M4.26 5.67C3 7.19 2.25 9.04 2.05 11H4.05C4.24 9.58 4.8 8.23 5.69 7.1L4.26 5.67M2.06 13C2.26 14.96 3.03 16.81 4.27 18.33L5.69 16.9C4.81 15.77 4.24 14.42 4.06 13H2.06M7.1 18.37L5.67 19.74C7.18 21 9.04 21.79 11 22V20C9.58 19.82 8.23 19.25 7.1 18.37M12 7L8 12H11V17L16 12H13L12 7Z"/>
                </svg>
                <h3>No MCP Servers Configured</h3>
                <p>This assistant has no MCP servers configured</p>
            </div>
        `;
    },

    showMcpError(message) {
        const contentElement = document.getElementById('mcp-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="skills-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading MCP Servers</h3>
                <p>${message}</p>
            </div>
        `;
    },

    async loadChannels() {
        if (!this.agentName) {
            this.showChannelsError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('channels-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="channels-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading channels...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/channels`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const channels = await response.json();

            if (!channels || channels.length === 0) {
                this.showChannelsEmpty();
                return;
            }

            this.displayChannels(channels);
        } catch (error) {
            console.error('Error loading channels:', error);
            if (error.name === 'AbortError') {
                this.showChannelsError('Request timed out. Please try again.');
            } else {
                this.showChannelsError('Failed to load channels. Please try again.');
            }
            Notifications.error('Failed to load channels', { duration: 5000 });
        }
    },

    displayChannels(channels) {
        const contentElement = document.getElementById('channels-content');
        if (!contentElement) return;

        const html = channels.map(ch => {
            const name = ch.name || '';
            const displayName = name.split('-')
                .map(w => w.charAt(0).toUpperCase() + w.slice(1))
                .join(' ');
            const source = ch.source ? `<span class="channel-source">${this.escapeHtml(ch.source)}</span>` : '';
            return `
                <div class="channel-item">
                    <img src="/assets/channel/${this.escapeHtml(name)}.png" alt="${this.escapeHtml(displayName)}"
                         class="channel-icon" onerror="this.style.display='none'">
                    <div class="channel-info">
                        <span class="channel-name">${this.escapeHtml(displayName)}</span>
                        ${source}
                    </div>
                </div>
            `;
        }).join('');

        contentElement.innerHTML = `<div class="channels-list">${html}</div>`;
    },

    showChannelsEmpty() {
        const contentElement = document.getElementById('channels-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="channels-empty">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
                </svg>
                <h3>No Channels</h3>
                <p>This assistant has no channels configured</p>
            </div>
        `;
    },

    showChannelsError(message) {
        const contentElement = document.getElementById('channels-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="channels-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Channels</h3>
                <p>${message}</p>
            </div>
        `;
    },

    async loadMarketplaces() {
        if (!this.agentName) {
            this.showMarketplacesError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('marketplaces-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="marketplaces-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading marketplaces...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            console.log(`fetching /assistants/${this.agentName}/marketplaces`);
            const response = await fetch(`/assistants/${this.agentName}/marketplaces`, {
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const marketplaces = await response.json();

            if (!marketplaces || marketplaces.length === 0) {
                this.showMarketplacesEmpty();
                return;
            }

            this.displayMarketplaces(marketplaces);
        } catch (error) {
            console.error('Error loading marketplaces:', error);
            if (error.name === 'AbortError') {
                this.showMarketplacesError('Request timed out. Please try again.');
            } else {
                this.showMarketplacesError('Failed to load marketplaces. Please try again.');
            }
            Notifications.error('Failed to load marketplaces', { duration: 5000 });
        }
    },

    displayMarketplaces(marketplaces) {
        const contentElement = document.getElementById('marketplaces-content');
        if (!contentElement) return;

        const html = marketplaces.map(mp => {
            const iconHtml = mp.icon
                ? `<img src="${this.escapeHtml(mp.icon)}" alt="${this.escapeHtml(mp.name)}" class="channel-icon" onerror="this.style.display='none'">`
                : '';
            const skillsHtml = mp.skills && mp.skills.length > 0
                ? mp.skills.map(s => `<span class="marketplace-skill-tag">${this.escapeHtml(s)}</span>`).join('')
                : '<span class="marketplace-no-skills">No skills</span>';

            return `
                <div class="marketplace-item">
                    <div class="marketplace-header">
                        ${iconHtml}
                        <h3 class="marketplace-name">${this.escapeHtml(mp.name)}</h3>
                        <span class="marketplace-skill-count">${mp.skills ? mp.skills.length : 0} skill${mp.skills && mp.skills.length === 1 ? '' : 's'}</span>
                    </div>
                    <p class="marketplace-url">${this.escapeHtml(mp.repoUrl)}</p>
                    <div class="marketplace-skills">${skillsHtml}</div>
                </div>
            `;
        }).join('');

        contentElement.innerHTML = `<div class="marketplaces-list">${html}</div>`;
    },

    showMarketplacesEmpty() {
        const contentElement = document.getElementById('marketplaces-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="marketplaces-empty">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z"/>
                </svg>
                <h3>No Marketplaces</h3>
                <p>This assistant has no skill marketplaces configured</p>
            </div>
        `;
    },

    showMarketplacesError(message) {
        const contentElement = document.getElementById('marketplaces-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="marketplaces-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Marketplaces</h3>
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
