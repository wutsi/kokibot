/**
 * Settings Page Controller
 * Manages settings page tabs and agent info loading
 */
const Settings = {
    agentName: null,
    tabs: [],
    tabContents: [],
    chatButton: null,
    backToChatLink: null,
    heartbeatData: null,
    heartbeatOriginalContent: null,
    isEditingHeartbeat: false,
    generalInstructionsContent: null,
    generalDescription: null,
    memoryData: null,
    kbData: null,
    kbPollingInterval: null,

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
        this.chatButton = document.getElementById('chat-btn');
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

        // Back to Chat button
        if (this.chatButton) {
            this.chatButton.addEventListener('click', () => {
                this.navigateToChat();
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
            case 'memory':
                this.loadMemory();
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
            case 'knowledge-base':
                this.loadKnowledgeBase();
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
        const urlTab = new URLSearchParams(window.location.search).get('tab');
        const savedTab = localStorage.getItem('settings_active_tab');
        const activeTab = urlTab || savedTab || 'general';

        this.switchTab(activeTab);

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

        const name = agentData.name || '';
        const description = agentData.description || '';
        const instructions = agentData.instructions || '';
        const firstName = agentData.firstName || '';
        const email = agentData.email || '';
        const language = agentData.language || '';
        const iconUrl = `/assistants/${this.escapeHtml(this.agentName)}/icon.png`;

        this.generalInstructionsContent = instructions;
        this.generalDescription = description;

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
                            <div class="general-name-row" id="general-name-row">
                                <span class="general-agent-name" id="general-name-text">${this.escapeHtml(name)}</span>
                                <button class="general-description-edit-btn" id="general-name-edit-btn" title="Edit name">
                                    <svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                                    </svg>
                                </button>
                            </div>
                            <div class="general-description-row">
                                <p class="general-agent-description" id="general-description-text">${description ? this.escapeHtml(description) : '<span class="general-description-placeholder">No description</span>'}</p>
                                <button class="general-description-edit-btn" id="general-description-edit-btn" title="Edit description">
                                    <svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="general-section">
                    <h3 class="general-section-title">Identity</h3>
                    <div class="identity-setting-row">
                        <div class="identity-setting-label-group">
                            <span class="identity-setting-name">Full Name</span>
                            <span class="identity-setting-hint">Display name for this assistant</span>
                        </div>
                        <input type="text" id="identity-full-name-input" class="identity-text-input"
                               value="${this.escapeHtml(firstName)}" placeholder="Not set">
                    </div>
                    <div class="identity-setting-row">
                        <div class="identity-setting-label-group">
                            <span class="identity-setting-name">Email</span>
                            <span class="identity-setting-hint">Email address of this assistant</span>
                        </div>
                        <input type="email" id="identity-email-input" class="identity-text-input"
                               value="${this.escapeHtml(email)}" placeholder="Not set">
                    </div>
                    <div class="identity-setting-row identity-setting-row-last">
                        <div class="identity-setting-label-group">
                            <span class="identity-setting-name">Language</span>
                            <span class="identity-setting-hint">Preferred language for responses</span>
                        </div>
                        <select id="identity-language-input" class="identity-language-select">
                            ${this.buildLanguageOptions(language)}
                        </select>
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

        document.getElementById('general-name-edit-btn')?.addEventListener('click', () => {
            this.enterNameEditMode();
        });

        document.getElementById('general-description-edit-btn')?.addEventListener('click', () => {
            this.enterDescriptionEditMode();
        });

        this.bindIdentityEvents();

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

    async loadMemory() {
        if (!this.agentName) return;

        const contentElement = document.getElementById('memory-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="memory-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading memory settings...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/memory`, { signal: controller.signal });
            clearTimeout(timeoutId);

            const memoryData = response.ok ? await response.json() : null;
            this.displayMemory(memoryData);
        } catch (error) {
            console.error('Error loading memory settings:', error);
            contentElement.innerHTML = `<p class="settings-error">Failed to load memory settings. Please try again.</p>`;
            Notifications.error('Failed to load memory settings', { duration: 5000 });
        }
    },

    displayMemory(memoryData) {
        const contentElement = document.getElementById('memory-content');
        if (!contentElement) return;

        this.memoryData = memoryData || { enabled: true, maxLength: 10240, window: 7 };

        const memoryEnabled = this.memoryData.enabled;
        const memoryMaxLengthKb = Math.max(1, Math.round(this.memoryData.maxLength / 1024));
        const memoryWindow = this.memoryData.window;
        const memoryFieldsClass = memoryEnabled ? '' : ' memory-fields-disabled';

        contentElement.innerHTML = `
            <div class="memory-setting-row">
                <div class="memory-setting-label-group">
                    <span class="memory-setting-name">Enable Memory</span>
                    <span class="memory-setting-hint">Store long-term and short-term memories</span>
                </div>
                <label class="memory-toggle" title="Toggle memory">
                    <input type="checkbox" id="memory-enabled-toggle" ${memoryEnabled ? 'checked' : ''}>
                    <span class="memory-toggle-slider"></span>
                </label>
            </div>
            <div id="memory-numeric-fields" class="${memoryFieldsClass}">
                <div class="memory-setting-row">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Max Length</span>
                        <span class="memory-setting-hint">Maximum memory file size (min. 1 KB)</span>
                    </div>
                    <div class="memory-number-control">
                        <input type="number" id="memory-max-length-input" class="memory-number-input"
                               min="1" value="${memoryMaxLengthKb}" ${memoryEnabled ? '' : 'disabled'}>
                        <span class="memory-number-unit">KB</span>
                    </div>
                </div>
                <div class="memory-setting-row memory-setting-row-last">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Window</span>
                        <span class="memory-setting-hint">Days of history to remember</span>
                    </div>
                    <div class="memory-number-control">
                        <input type="number" id="memory-window-input" class="memory-number-input"
                               min="1" value="${memoryWindow}" ${memoryEnabled ? '' : 'disabled'}>
                        <span class="memory-number-unit">days</span>
                    </div>
                </div>
            </div>
        `;

        this.bindMemoryEvents();
    },

    buildLanguageOptions(selected) {
        const languages = [
            { code: '',   label: 'Not set' },
            { code: 'ar', label: 'Arabic' },
            { code: 'zh', label: 'Chinese' },
            { code: 'nl', label: 'Dutch' },
            { code: 'en', label: 'English' },
            { code: 'fr', label: 'French' },
            { code: 'de', label: 'German' },
            { code: 'hi', label: 'Hindi' },
            { code: 'id', label: 'Indonesian' },
            { code: 'it', label: 'Italian' },
            { code: 'ja', label: 'Japanese' },
            { code: 'ko', label: 'Korean' },
            { code: 'pl', label: 'Polish' },
            { code: 'pt', label: 'Portuguese' },
            { code: 'ru', label: 'Russian' },
            { code: 'es', label: 'Spanish' },
            { code: 'sv', label: 'Swedish' },
            { code: 'tr', label: 'Turkish' },
            { code: 'uk', label: 'Ukrainian' },
            { code: 'vi', label: 'Vietnamese' },
        ];
        return languages
            .map(({ code, label }) => `<option value="${code}" ${selected === code ? 'selected' : ''}>${label}</option>`)
            .join('');
    },

    bindIdentityEvents() {
        const fullNameInput = document.getElementById('identity-full-name-input');
        if (fullNameInput) {
            const save = () => this.saveAssistantSetting('assistant.full-name', fullNameInput.value.trim(), 'Full name saved');
            fullNameInput.addEventListener('blur', save);
            fullNameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') fullNameInput.blur(); });
        }

        const emailInput = document.getElementById('identity-email-input');
        if (emailInput) {
            const save = () => {
                const value = emailInput.value.trim();
                if (value && !emailInput.validity.valid) {
                    Notifications.error('Please enter a valid email address.');
                    return;
                }
                this.saveAssistantSetting('assistant.email', value, 'Email saved');
            };
            emailInput.addEventListener('blur', save);
            emailInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') emailInput.blur(); });
        }

        const languageSelect = document.getElementById('identity-language-input');
        if (languageSelect) {
            languageSelect.addEventListener('change', () => {
                this.saveAssistantSetting('assistant.language', languageSelect.value, 'Language saved');
            });
        }
    },

    async saveAssistantSetting(key, value, successMsg, onSuccess = null) {
        if (!this.agentName) return;
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value }),
                signal: controller.signal,
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.error || `Failed to save (${response.status})`);
            }

            Notifications.success(successMsg, { duration: 3000 });
            if (onSuccess) onSuccess();
        } catch (error) {
            console.error('Error saving assistant setting:', error);
            Notifications.error(error.name === 'AbortError' ? 'Save request timed out.' : error.message || 'Failed to save. Please try again.');
        }
    },

    bindMemoryEvents() {
        document.getElementById('memory-enabled-toggle')?.addEventListener('change', (e) => {
            this.saveMemorySetting('enabled', e.target.checked, e.target.checked ? 'Memory enabled' : 'Memory disabled');
            this.updateMemoryFields(e.target.checked);
        });

        const maxLengthInput = document.getElementById('memory-max-length-input');
        if (maxLengthInput) {
            const saveMaxLength = () => {
                const kb = Math.max(1, parseInt(maxLengthInput.value, 10) || 1);
                maxLengthInput.value = kb;
                this.saveMemorySetting('max-length', kb * 1024, 'Max length saved');
            };
            maxLengthInput.addEventListener('blur', saveMaxLength);
            maxLengthInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { maxLengthInput.blur(); } });
        }

        const windowInput = document.getElementById('memory-window-input');
        if (windowInput) {
            const saveWindow = () => {
                const days = Math.max(1, parseInt(windowInput.value, 10) || 1);
                windowInput.value = days;
                this.saveMemorySetting('window', `${days}d`, 'Window saved');
            };
            windowInput.addEventListener('blur', saveWindow);
            windowInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { windowInput.blur(); } });
        }
    },

    updateMemoryFields(enabled) {
        const container = document.getElementById('memory-numeric-fields');
        if (!container) return;
        container.classList.toggle('memory-fields-disabled', !enabled);
        container.querySelectorAll('input, button').forEach(el => {
            el.disabled = !enabled;
        });
    },

    async saveMemorySetting(key, value, successMsg) {
        if (!this.agentName) return;
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/memory/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value }),
                signal: controller.signal,
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.error || `Failed to save (${response.status})`);
            }

            Notifications.success(successMsg, { duration: 3000 });
        } catch (error) {
            console.error('Error saving memory setting:', error);
            Notifications.error(error.name === 'AbortError' ? 'Save request timed out.' : error.message || 'Failed to save. Please try again.');
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

            const response = await fetch(`/assistants/${this.agentName}/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key: 'assistant.instructions', value: content }),
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

    enterNameEditMode() {
        const row = document.getElementById('general-name-row');
        if (!row) return;

        const currentName = document.getElementById('general-name-text')?.textContent || this.agentName;
        row.innerHTML = `
            <input type="text" class="general-name-input" id="general-name-input"
                   value="${this.escapeHtml(currentName)}" placeholder="Agent name"
                   autocomplete="off" spellcheck="false">
            <button class="settings-action-btn settings-action-btn-primary" id="general-name-save-btn">Save</button>
            <button class="settings-action-btn settings-action-btn-secondary" id="general-name-cancel-btn">Cancel</button>
        `;

        const input = document.getElementById('general-name-input');
        input?.focus();
        input?.select();

        document.getElementById('general-name-save-btn')?.addEventListener('click', () => this.saveAgentName());
        document.getElementById('general-name-cancel-btn')?.addEventListener('click', () => this.exitNameEditMode());
        input?.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') this.saveAgentName();
            if (e.key === 'Escape') this.exitNameEditMode();
        });
    },

    async saveAgentName() {
        const input = document.getElementById('general-name-input');
        if (!input) return;

        const newName = input.value.trim();
        if (!newName) {
            Notifications.error('Name cannot be empty.');
            return;
        }

        const saveBtn = document.getElementById('general-name-save-btn');
        const cancelBtn = document.getElementById('general-name-cancel-btn');
        if (saveBtn) saveBtn.disabled = true;
        if (cancelBtn) cancelBtn.disabled = true;

        await this.saveAssistantSetting('assistant.name', newName, 'Name saved', () => {
            this.agentName = newName;
            const url = new URL(window.location.href);
            url.searchParams.set('agent', newName);
            window.history.replaceState({}, '', url.toString());
            const sidebarName = document.getElementById('sidebar-agent-name');
            if (sidebarName) sidebarName.textContent = newName;
        });

        if (saveBtn) saveBtn.disabled = false;
        if (cancelBtn) cancelBtn.disabled = false;
        this.exitNameEditMode();
    },

    exitNameEditMode() {
        const row = document.getElementById('general-name-row');
        if (!row) return;

        const name = this.agentName;
        row.innerHTML = `
            <span class="general-agent-name" id="general-name-text">${this.escapeHtml(name)}</span>
            <button class="general-description-edit-btn" id="general-name-edit-btn" title="Edit name">
                <svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14">
                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                </svg>
            </button>
        `;

        document.getElementById('general-name-edit-btn')?.addEventListener('click', () => this.enterNameEditMode());
    },

    enterDescriptionEditMode() {
        const row = document.getElementById('general-description-text')?.parentElement;
        if (!row) return;

        row.innerHTML = `
            <input type="text" class="general-description-input" id="general-description-input"
                   value="${this.escapeHtml(this.generalDescription || '')}" placeholder="Enter description...">
            <button class="settings-action-btn settings-action-btn-primary general-description-save-btn" id="general-description-save-btn">Save</button>
            <button class="settings-action-btn settings-action-btn-secondary general-description-cancel-btn" id="general-description-cancel-btn">Cancel</button>
        `;

        const input = document.getElementById('general-description-input');
        input?.focus();
        input?.select();

        document.getElementById('general-description-save-btn')?.addEventListener('click', () => this.saveDescription());
        document.getElementById('general-description-cancel-btn')?.addEventListener('click', () => this.exitDescriptionEditMode());
        input?.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') this.saveDescription();
            if (e.key === 'Escape') this.exitDescriptionEditMode();
        });
    },

    async saveDescription() {
        const input = document.getElementById('general-description-input');
        if (!input) return;

        const value = input.value.trim();
        const saveBtn = document.getElementById('general-description-save-btn');
        const cancelBtn = document.getElementById('general-description-cancel-btn');

        if (saveBtn) saveBtn.disabled = true;
        if (cancelBtn) cancelBtn.disabled = true;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`/assistants/${this.agentName}/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key: 'assistant.description', value }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to save description (${response.status})`);
            }

            this.generalDescription = value;
            this.exitDescriptionEditMode();
            Notifications.success('Description saved', { duration: 3000 });
        } catch (error) {
            console.error('Error saving description:', error);
            if (saveBtn) saveBtn.disabled = false;
            if (cancelBtn) cancelBtn.disabled = false;
            Notifications.error(error.name === 'AbortError' ? 'Save request timed out. Please try again.' : 'Failed to save description. Please try again.');
        }
    },

    exitDescriptionEditMode() {
        const row = document.getElementById('general-description-input')?.parentElement;
        if (!row) return;

        const text = this.generalDescription || '';
        row.innerHTML = `
            <p class="general-agent-description" id="general-description-text">${text ? this.escapeHtml(text) : '<span class="general-description-placeholder">No description</span>'}</p>
            <button class="general-description-edit-btn" id="general-description-edit-btn" title="Edit description">
                <svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14">
                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                </svg>
            </button>
        `;

        document.getElementById('general-description-edit-btn')?.addEventListener('click', () => this.enterDescriptionEditMode());
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
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="heartbeat-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading heartbeat settings...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/heartbeat`, { signal: controller.signal });
            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`Failed to load heartbeat settings (${response.status})`);

            const data = await response.json();
            this.heartbeatData = data;
            this.heartbeatOriginalContent = data.instructions || '';

            this.renderHeartbeat(contentElement);
        } catch (error) {
            console.error('Error loading heartbeat settings:', error);
            this.showHeartbeatError(error.name === 'AbortError' ? 'Request timed out. Please try again.' : 'Failed to load heartbeat settings. Please try again.');
            Notifications.error('Failed to load heartbeat settings', { duration: 5000 });
        }
    },

    renderHeartbeat(contentElement) {
        const data = this.heartbeatData;
        const enabled = data.enabled !== false;
        const frequency = data.frequency || "1h";
        const disabledClass = enabled ? '' : ' memory-fields-disabled';

        const FREQUENCY_OPTIONS = [
            { value: '30m', label: 'Every 30 minutes' },
            { value: '1h', label: 'Every hour' },
            { value: '2h', label: 'Every 2 hours' },
            { value: '3h', label: 'Every 3 hours' },
            { value: '4h', label: 'Every 4 hours' },
            { value: '5h', label: 'Every 5 hours' },
            { value: '6h', label: 'Every 6 hours' },
            { value: '7h', label: 'Every 7 hours' },
            { value: '8h', label: 'Every 8 hours' },
            { value: '9h', label: 'Every 9 hours' },
            { value: '10h', label: 'Every 10 hours' },
            { value: '11h', label: 'Every 11 hours' },
            { value: '12h', label: 'Every 12 hours' },
            { value: '1d', label: 'Every day' },
        ];

        const frequencyOptions = FREQUENCY_OPTIONS.map(opt =>
            `<option value="${opt.value}"${frequency === opt.value ? ' selected' : ''}>${opt.label}</option>`
        ).join('');

        contentElement.innerHTML = `
            <div class="heartbeat-settings">
                <div class="memory-setting-row">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Enable Heartbeat</span>
                        <span class="memory-setting-hint">Run periodic tasks automatically</span>
                    </div>
                    <label class="memory-toggle" title="Toggle heartbeat">
                        <input type="checkbox" id="heartbeat-enabled-toggle"${enabled ? ' checked' : ''}>
                        <span class="memory-toggle-slider"></span>
                    </label>
                </div>
                <div id="heartbeat-schedule-fields" class="memory-setting-row memory-setting-row-last${disabledClass}">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Frequency</span>
                        <span class="memory-setting-hint">How often to run the heartbeat</span>
                    </div>
                    <select id="heartbeat-frequency-select" class="heartbeat-frequency-select"${enabled ? '' : ' disabled'}>
                        ${frequencyOptions}
                    </select>
                </div>
            </div>
            <div class="heartbeat-instructions-section">
                <div class="settings-section-header heartbeat-instructions-header">
                    <div>
                        <h3 class="general-section-title">Instructions</h3>
                        <span class="memory-setting-hint">Periodic prompt sent to the assistant on each heartbeat tick</span>
                    </div>
                    <div class="settings-section-actions">
                        <button class="settings-action-btn settings-action-btn-secondary" id="heartbeat-edit-btn"${enabled ? '' : ' disabled'}>
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                            </svg>
                            Edit
                        </button>
                        <button class="settings-action-btn settings-action-btn-primary" id="heartbeat-save-btn" style="display:none">
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"/>
                            </svg>
                            Save
                        </button>
                        <button class="settings-action-btn settings-action-btn-secondary" id="heartbeat-cancel-btn" style="display:none">
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
                            </svg>
                            Cancel
                        </button>
                    </div>
                </div>
                <div id="heartbeat-instructions-content">
                    ${this.heartbeatOriginalContent.trim()
                        ? `<div class="heartbeat-text markdown-body">${new MarkdownRenderer().render(this.heartbeatOriginalContent)}</div>`
                        : `<div class="heartbeat-error">
                               <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
                                   <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                               </svg>
                               <h3>No Instructions</h3>
                               <p>No heartbeat instructions configured</p>
                           </div>`
                    }
                </div>
            </div>
        `;

        this.setupHeartbeatListeners();
    },

    setupHeartbeatListeners() {
        document.getElementById('heartbeat-enabled-toggle')?.addEventListener('change', (e) => {
            const enabled = e.target.checked;
            this.saveHeartbeatSetting('enabled', enabled, enabled ? 'Heartbeat enabled' : 'Heartbeat disabled');
            const scheduleFields = document.getElementById('heartbeat-schedule-fields');
            if (scheduleFields) {
                scheduleFields.classList.toggle('memory-fields-disabled', !enabled);
                const select = document.getElementById('heartbeat-frequency-select');
                if (select) select.disabled = !enabled;
            }
            const editBtn = document.getElementById('heartbeat-edit-btn');
            if (editBtn) editBtn.disabled = !enabled;
        });

        document.getElementById('heartbeat-frequency-select')?.addEventListener('change', (e) => {
            this.saveHeartbeatSetting('frequency', e.target.value, 'Frequency saved');
        });

        document.getElementById('heartbeat-edit-btn')?.addEventListener('click', () => this.enterHeartbeatEditMode());
        document.getElementById('heartbeat-save-btn')?.addEventListener('click', () => this.saveHeartbeat());
        document.getElementById('heartbeat-cancel-btn')?.addEventListener('click', () => this.exitHeartbeatEditMode());
    },

    showHeartbeatError(message) {
        const contentElement = document.getElementById('heartbeat-content');
        if (!contentElement) return;
        contentElement.innerHTML = `
            <div class="heartbeat-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Heartbeat Settings</h3>
                <p>${message}</p>
            </div>
        `;
    },

    enterHeartbeatEditMode() {
        const instructionsContent = document.getElementById('heartbeat-instructions-content');
        if (!instructionsContent) return;

        this.isEditingHeartbeat = true;

        const textarea = document.createElement('textarea');
        textarea.className = 'heartbeat-editor';
        textarea.value = this.heartbeatOriginalContent || '';
        textarea.id = 'heartbeat-editor-textarea';

        instructionsContent.innerHTML = '';
        instructionsContent.appendChild(textarea);
        textarea.focus();

        document.getElementById('heartbeat-edit-btn').style.display = 'none';
        document.getElementById('heartbeat-save-btn').style.display = 'flex';
        document.getElementById('heartbeat-cancel-btn').style.display = 'flex';
    },

    async saveHeartbeat() {
        if (!this.agentName) { Notifications.error('No agent selected'); return; }

        const textarea = document.getElementById('heartbeat-editor-textarea');
        if (!textarea) return;

        const content = textarea.value;
        const saveBtn = document.getElementById('heartbeat-save-btn');
        const cancelBtn = document.getElementById('heartbeat-cancel-btn');
        if (saveBtn) saveBtn.disabled = true;
        if (cancelBtn) cancelBtn.disabled = true;

        try {
            await this.saveHeartbeatSetting('instructions', content, 'Instructions saved');
            this.heartbeatOriginalContent = content;
            this.exitHeartbeatEditMode();
        } finally {
            if (saveBtn) saveBtn.disabled = false;
            if (cancelBtn) cancelBtn.disabled = false;
        }
    },

    exitHeartbeatEditMode() {
        this.isEditingHeartbeat = false;

        const instructionsContent = document.getElementById('heartbeat-instructions-content');
        if (!instructionsContent) return;

        if (this.heartbeatOriginalContent?.trim()) {
            instructionsContent.innerHTML = `<div class="heartbeat-text markdown-body">${new MarkdownRenderer().render(this.heartbeatOriginalContent)}</div>`;
        } else {
            instructionsContent.innerHTML = `
                <div class="heartbeat-error">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                    </svg>
                    <h3>No Instructions</h3>
                    <p>No heartbeat instructions configured</p>
                </div>`;
        }

        document.getElementById('heartbeat-edit-btn').style.display = 'flex';
        document.getElementById('heartbeat-save-btn').style.display = 'none';
        document.getElementById('heartbeat-cancel-btn').style.display = 'none';
    },

    async saveHeartbeatSetting(key, value, successMsg) {
        if (!this.agentName) return;
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/heartbeat/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value }),
                signal: controller.signal,
            });
            clearTimeout(timeoutId);
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.error || `Failed to save (${response.status})`);
            }
            Notifications.success(successMsg, { duration: 3000 });
        } catch (error) {
            console.error('Error saving heartbeat setting:', error);
            Notifications.error(error.name === 'AbortError' ? 'Save request timed out.' : error.message || 'Failed to save. Please try again.');
        }
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
                : `<div class="marketplace-icon-placeholder"><svg fill="currentColor" height="24" viewBox="0 0 24 24" width="24"><path d="M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z"/></svg></div>`;
            const skillsHtml = mp.skills && mp.skills.length > 0
                ? mp.skills.map(s => `<span class="marketplace-skill-tag">${this.escapeHtml(s)}</span>`).join('')
                : '<span class="marketplace-no-skills">No skills</span>';

            return `
                <div class="marketplace-item">
                    ${iconHtml}
                    <div class="marketplace-info">
                        <div class="marketplace-info-header">
                            <span class="marketplace-name">${this.escapeHtml(mp.name)}</span>
                            <span class="marketplace-skill-count">${mp.skills ? mp.skills.length : 0} skill${mp.skills && mp.skills.length === 1 ? '' : 's'}</span>
                        </div>
                        ${mp.description ? `<span class="marketplace-description">${this.escapeHtml(mp.description)}</span>` : ''}
                        <span class="marketplace-url">${this.escapeHtml(mp.repoUrl)}</span>
                        <div class="marketplace-skills">${skillsHtml}</div>
                    </div>
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

    async loadKnowledgeBase() {
        if (!this.agentName) {
            this.showKBError('No agent selected');
            return;
        }

        const contentElement = document.getElementById('kb-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="kb-loading">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" class="loading-spinner">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                <p>Loading knowledge base settings...</p>
            </div>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/knowledge-base`, { signal: controller.signal });
            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`Failed to load knowledge base settings (${response.status})`);

            this.kbData = await response.json();
            this.renderKnowledgeBase(contentElement);
        } catch (error) {
            console.error('Error loading knowledge base settings:', error);
            this.showKBError(error.name === 'AbortError' ? 'Request timed out. Please try again.' : 'Failed to load knowledge base settings. Please try again.');
            Notifications.error('Failed to load knowledge base settings', { duration: 5000 });
        }
    },

    renderKnowledgeBase(contentElement) {
        const enabled = this.kbData.enabled !== false;
        const exclusive = this.kbData.exclusive !== false;
        const webSearch = this.kbData.webSearch !== false;

        contentElement.innerHTML = `
            <div class="heartbeat-settings">
                <div class="memory-setting-row">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Enable Knowledge Base</span>
                        <span class="memory-setting-hint">Use the knowledge base to answer queries</span>
                    </div>
                    <label class="memory-toggle" title="Toggle knowledge base">
                        <input type="checkbox" id="kb-enabled-toggle"${enabled ? ' checked' : ''}>
                        <span class="memory-toggle-slider"></span>
                    </label>
                </div>
                <div class="memory-setting-row${enabled ? '' : ' memory-fields-disabled'}" id="kb-exclusive-row">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Exclusive Mode</span>
                        <span class="memory-setting-hint">Search only the knowledge base, not the LLM training data</span>
                    </div>
                    <label class="memory-toggle" title="Toggle exclusive mode">
                        <input type="checkbox" id="kb-exclusive-toggle"${exclusive ? ' checked' : ''}${enabled ? '' : ' disabled'}>
                        <span class="memory-toggle-slider"></span>
                    </label>
                </div>
                <div class="memory-setting-row memory-setting-row-last${enabled ? '' : ' memory-fields-disabled'}" id="kb-web-search-row">
                    <div class="memory-setting-label-group">
                        <span class="memory-setting-name">Web Search</span>
                        <span class="memory-setting-hint">Allow web search to supplement knowledge base answers</span>
                    </div>
                    <label class="memory-toggle" title="Toggle web search">
                        <input type="checkbox" id="kb-web-search-toggle"${webSearch ? ' checked' : ''}${enabled ? '' : ' disabled'}>
                        <span class="memory-toggle-slider"></span>
                    </label>
                </div>
            </div>
            <div class="heartbeat-instructions-section${enabled ? '' : ' memory-fields-disabled'}" id="kb-files-section">
                <div class="settings-section-header heartbeat-instructions-header">
                    <div>
                        <h3 class="general-section-title" id="kb-files-title">Files</h3>
                        <span class="memory-setting-hint">Documents ingested into the knowledge base</span>
                    </div>
                    <div class="settings-section-actions">
                        <button class="settings-action-btn settings-action-btn-secondary" id="kb-link-btn"${enabled ? '' : ' disabled'}>
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/>
                            </svg>
                            Link URL
                        </button>
                        <button class="settings-action-btn settings-action-btn-primary" id="kb-upload-btn"${enabled ? '' : ' disabled'}>
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
                            </svg>
                            Upload
                        </button>
                        <input type="file" id="kb-file-input" style="display:none;">
                    </div>
                </div>
                <div id="kb-files-list"></div>
            </div>
        `;

        this.setupKBListeners();
        document.getElementById('kb-link-btn')?.addEventListener('click', () => {
            this.ingestKBLink();
        });
        document.getElementById('kb-upload-btn')?.addEventListener('click', () => {
            document.getElementById('kb-file-input')?.click();
        });
        document.getElementById('kb-file-input')?.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) this.uploadKBFile(file);
            e.target.value = '';
        });
        this.loadKBFiles();
    },

    setupKBListeners() {
        document.getElementById('kb-enabled-toggle')?.addEventListener('change', (e) => {
            const enabled = e.target.checked;
            this.saveKBSetting('enabled', enabled, enabled ? 'Knowledge base enabled' : 'Knowledge base disabled');
            this.updateKBFields(enabled);
        });
        document.getElementById('kb-exclusive-toggle')?.addEventListener('change', (e) => {
            this.saveKBSetting('exclusive', e.target.checked, e.target.checked ? 'Exclusive mode enabled' : 'Exclusive mode disabled');
        });
        document.getElementById('kb-web-search-toggle')?.addEventListener('change', (e) => {
            this.saveKBSetting('webSearch', e.target.checked, e.target.checked ? 'Web search enabled' : 'Web search disabled');
        });
    },

    updateKBFields(enabled) {
        const exclusiveRow = document.getElementById('kb-exclusive-row');
        const filesSection = document.getElementById('kb-files-section');
        if (exclusiveRow) {
            exclusiveRow.classList.toggle('memory-fields-disabled', !enabled);
            const toggle = document.getElementById('kb-exclusive-toggle');
            if (toggle) toggle.disabled = !enabled;
        }
        const webSearchRow = document.getElementById('kb-web-search-row');
        if (webSearchRow) {
            webSearchRow.classList.toggle('memory-fields-disabled', !enabled);
            const toggle = document.getElementById('kb-web-search-toggle');
            if (toggle) toggle.disabled = !enabled;
        }
        if (filesSection) {
            filesSection.classList.toggle('memory-fields-disabled', !enabled);
            const linkBtn = document.getElementById('kb-link-btn');
            if (linkBtn) linkBtn.disabled = !enabled;
            const uploadBtn = document.getElementById('kb-upload-btn');
            if (uploadBtn) uploadBtn.disabled = !enabled;
            filesSection.querySelectorAll('.kb-delete-btn').forEach(btn => { btn.disabled = !enabled; });
        }
    },

    async saveKBSetting(key, value, successMsg) {
        if (!this.agentName) return;
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/knowledge-base/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value }),
                signal: controller.signal,
            });
            clearTimeout(timeoutId);
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.error || `Failed to save (${response.status})`);
            }
            Notifications.success(successMsg, { duration: 3000 });
        } catch (error) {
            console.error('Error saving knowledge base setting:', error);
            Notifications.error(error.name === 'AbortError' ? 'Save request timed out.' : error.message || 'Failed to save. Please try again.');
        }
    },

    showKBError(message) {
        const contentElement = document.getElementById('kb-content');
        if (!contentElement) return;

        contentElement.innerHTML = `
            <div class="kb-error">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <h3>Error Loading Knowledge Base Settings</h3>
                <p>${this.escapeHtml(message)}</p>
            </div>
        `;
    },

    async loadKBFiles() {
        const listEl = document.getElementById('kb-files-list');
        if (!listEl) return;

        listEl.innerHTML = Array.from({ length: 3 }, () => `
            <div class="kb-skeleton-row">
                <div class="kb-skeleton-line kb-skeleton-line--title"></div>
                <div class="kb-skeleton-line kb-skeleton-line--sub"></div>
            </div>
        `).join('');

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}/knowledge-base/entries?limit=20`, {
                signal: controller.signal,
            });
            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const entries = await response.json();
            this.renderKBFiles(entries);
        } catch (error) {
            console.error('Error loading KB files:', error);
            listEl.innerHTML = `
                <div class="skills-error">
                    <p>${error.name === 'AbortError' ? 'Request timed out.' : 'Failed to load files.'}</p>
                </div>
            `;
        }
    },

    renderKBFiles(entries) {
        const listEl = document.getElementById('kb-files-list');
        if (!listEl) return;

        const MAX_FILES = 20;
        this.kbEntries = entries || [];
        const count = this.kbEntries.length;
        const atMax = count >= MAX_FILES;

        const titleEl = document.getElementById('kb-files-title');
        if (titleEl) titleEl.textContent = count > 0 ? `Files (${count}/${MAX_FILES})` : 'Files';

        const kbEnabled = this.kbData?.enabled !== false;
        const uploadBtn = document.getElementById('kb-upload-btn');
        const linkBtn = document.getElementById('kb-link-btn');
        if (uploadBtn) uploadBtn.disabled = atMax || !kbEnabled;
        if (linkBtn) linkBtn.disabled = atMax || !kbEnabled;

        if (!entries || entries.length === 0) {
            listEl.innerHTML = `
                <div class="skills-empty">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"/>
                    </svg>
                    <h3>No Files</h3>
                    <p>No files have been ingested yet</p>
                </div>
            `;
            return;
        }

        const rowsHtml = entries.map(entry => {
            const status = entry.status || 'PROCESSING';
            const isReady = status === 'READY';
            const isError = status === 'ERROR';
            const isProcessing = status === 'PROCESSING';
            const titleHtml = this.buildKBEntryTitle(entry);

            const downloadBtn = entry.type === 'FILE'
                ? `<a href="${this.escapeHtml(entry.url)}" download title="Download file"
                      style="position:absolute;top:8px;right:36px;background:none;border:none;cursor:pointer;color:var(--text-secondary,#666);padding:4px;display:flex;align-items:center;justify-content:center;border-radius:4px;text-decoration:none;">
                       <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                           <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                       </svg>
                   </a>`
                : '';
            const actionButtons = `
                ${downloadBtn}
                <button class="kb-delete-btn" data-filename="${this.escapeHtml(entry.filename)}" title="Delete file"
                        style="position:absolute;top:8px;right:8px;background:none;border:none;cursor:pointer;color:var(--text-secondary,#666);padding:4px;display:flex;align-items:center;justify-content:center;border-radius:4px;">
                    <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                        <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                    </svg>
                </button>
            `;

            if (isProcessing) {
                return `
                    <div class="channel-item kb-skeleton-row--processing" style="flex-direction:column;align-items:flex-start;gap:4px;position:relative;">
                        ${actionButtons}
                        <span class="kb-processing-title">${titleHtml}</span>
                        <div class="kb-skeleton-line kb-skeleton-line--sub"></div>
                        <div class="kb-skeleton-line kb-skeleton-line--sub" style="width:50%;"></div>
                    </div>
                `;
            }

            const keywordsHtml = (entry.keywords || [])
                .map(k => `<span class="marketplace-skill-tag">${this.escapeHtml(k)}</span>`)
                .join('');

            const statusBadge = isError
                ? `<span style="display:inline-flex;align-items:center;gap:4px;font-size:11px;color:#c0392b;margin-left:6px;">
                       <svg fill="currentColor" height="12" viewBox="0 0 24 24" width="12"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
                       Error
                   </span>`
                : '';

            const errorRow = isError && entry.error
                ? `<span style="font-size:11px;color:#c0392b;">${this.escapeHtml(entry.error)}</span>`
                : '';

            const urlRow = entry.type === 'LINK' && entry.url
                ? `<a href="${this.escapeHtml(entry.url)}" target="_blank" rel="noopener noreferrer" class="channel-source" style="word-break:break-all;color:var(--color-accent-blue,#2196f3);">${this.escapeHtml(entry.url)}</a>`
                : '';

            return `
                <div class="channel-item" style="flex-direction:column;align-items:flex-start;gap:4px;position:relative;">
                    ${actionButtons}
                    <span style="display:inline-flex;align-items:center;gap:8px;">
                        ${titleHtml}
                        ${statusBadge}
                    </span>
                    ${urlRow}
                    ${errorRow}
                    ${isReady && entry.scope ? `<span class="channel-source">${this.escapeHtml(entry.scope)}</span>` : ''}
                    ${isReady && keywordsHtml ? `<div class="marketplace-skills" style="margin-top:4px;">${keywordsHtml}</div>` : ''}
                </div>
            `;
        }).join('');

        listEl.innerHTML = `<div class="channels-list">${rowsHtml}</div>`;

        const stillProcessing = entries.some(e => (e.status || 'PROCESSING') === 'PROCESSING');
        if (!stillProcessing) this.stopKBPolling();

        listEl.querySelectorAll('.kb-delete-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const filename = btn.dataset.filename;
                if (filename) this.deleteKBFile(btn, filename);
            });
        });
    },

    async uploadKBFile(file) {
        if ((this.kbEntries || []).length >= 20) {
            Notifications.error('Maximum of 20 files reached. Delete a file before uploading.', { duration: 5000 });
            return;
        }
        const btn = document.getElementById('kb-upload-btn');
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = `
                <svg class="loading-spinner" fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                Uploading…
            `;
        }

        const formData = new FormData();
        formData.append('file', file, file.name);

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 60000);
            const response = await fetch(`/assistants/${this.agentName}/knowledge-base/upload`, {
                method: 'POST',
                body: formData,
                signal: controller.signal,
            });
            clearTimeout(timeoutId);

            if (response.status === 409) {
                clearTimeout(timeoutId);
                Notifications.error('File already ingested', { duration: 5000 });
                return;
            }
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            Notifications.success(`${file.name} ingested successfully`, { duration: 3000 });
            await this.loadKBFiles();
            this.startKBPolling();
        } catch (error) {
            console.error('Error uploading KB file:', error);
            Notifications.error(
                error.name === 'AbortError' ? 'Upload timed out. Please try again.' : 'Failed to upload file. Please try again.',
                { duration: 5000 }
            );
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = `
                    <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                        <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
                    </svg>
                    Upload
                `;
            }
        }
    },

    async ingestKBLink() {
        if ((this.kbEntries || []).length >= 20) {
            Notifications.error('Maximum of 20 files reached. Delete a file before adding more.', { duration: 5000 });
            return;
        }
        const url = window.prompt('Enter URL to ingest:');
        if (!url) return;

        const btn = document.getElementById('kb-link-btn');
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = `
                <svg class="loading-spinner" fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                    <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
                </svg>
                Ingesting…
            `;
        }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 60000);
            const response = await fetch(`/assistants/${this.agentName}/knowledge-base/link`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url }),
                signal: controller.signal,
            });
            clearTimeout(timeoutId);

            if (response.status === 409) {
                Notifications.error('URL already ingested', { duration: 5000 });
                return;
            }
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            Notifications.success('URL ingested successfully', { duration: 3000 });
            await this.loadKBFiles();
            this.startKBPolling();
        } catch (error) {
            console.error('Error ingesting URL:', error);
            Notifications.error(
                error.name === 'AbortError' ? 'Request timed out. Please try again.' : 'Failed to ingest URL. Please try again.',
                { duration: 5000 }
            );
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = `
                    <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                        <path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/>
                    </svg>
                    Link URL
                `;
            }
        }
    },

    async deleteKBFile(btn, filename) {
        if (!window.confirm(`Delete "${filename}"?`)) return;
        btn.disabled = true;
        btn.innerHTML = `
            <svg class="loading-spinner" fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
            </svg>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(
                `/assistants/${this.agentName}/knowledge-base/entries/delete?filename=${encodeURIComponent(filename)}`,
                { signal: controller.signal }
            );
            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            Notifications.success(`${this.escapeHtml(filename)} deleted`, { duration: 3000 });
            await this.loadKBFiles();
        } catch (error) {
            console.error('Error deleting KB file:', error);
            btn.disabled = false;
            btn.innerHTML = `
                <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                    <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
            `;
            Notifications.error(
                error.name === 'AbortError' ? 'Delete request timed out.' : 'Failed to delete file. Please try again.',
                { duration: 5000 }
            );
        }
    },

    startKBPolling() {
        if (this.kbPollingInterval) return;
        this.kbPollingInterval = setInterval(() => this.loadKBFiles(), 30000);
    },

    stopKBPolling() {
        if (!this.kbPollingInterval) return;
        clearInterval(this.kbPollingInterval);
        this.kbPollingInterval = null;
    },

    buildKBEntryTitle(entry) {
        if (entry.type === 'LINK') {
            const url = entry.url || '';
            const urlPath = url.split('?')[0].split('#')[0];
            const lastSegment = urlPath.split('/').pop();
            const dotIdx = lastSegment.lastIndexOf('.');
            const ext = !url.endsWith('/') && dotIdx !== -1 ? lastSegment.slice(dotIdx + 1).toLowerCase() : '';
            const prefixHtml = ext
                ? `<span class="message-file-extension file-extension-${this.escapeHtml(ext)}">${this.escapeHtml(ext)}</span>`
                : `<svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14" style="flex-shrink:0;color:var(--text-secondary,#666);"><path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/></svg>`;
            return `${prefixHtml}<span class="channel-name">${this.escapeHtml(entry.displayName)}</span>`;
        }
        if (entry.type === 'FILE') {
            const dotIdx = entry.filename.lastIndexOf('.');
            const ext = dotIdx !== -1 ? entry.filename.slice(dotIdx + 1).toLowerCase() : '';
            const displayName = dotIdx !== -1 ? entry.filename.slice(0, dotIdx) : entry.filename;
            const extBadge = ext
                ? `<span class="message-file-extension file-extension-${this.escapeHtml(ext)}">${this.escapeHtml(ext)}</span>`
                : '';
            return `${extBadge}<span class="channel-name">${this.escapeHtml(displayName)}</span>`;
        }
        return `<span class="channel-name">${this.escapeHtml(entry.filename)}</span>`;
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
