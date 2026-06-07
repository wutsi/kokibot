/**
 * Chat UI management
 * Handles rendering messages, user interactions, and UI state
 */
const ChatUI = {
    wsClient: null,
    currentMessageId: null,
    reasoningChunks: [],
    currentToolStatus: null,

    init(agentName) {
        this.agentName = agentName || 'thoth';
        this.setupElements();
        this.setupEventListeners();
        this.loadAssistantInfo();
        this.connectWebSocket();
    },

    setupElements() {
        this.chatContainer = document.getElementById('chat-container');
        this.messageInput = document.getElementById('message-input');
        this.sendButton = document.getElementById('send-button');
        this.statusIndicator = document.getElementById('status-indicator');
        this.statusText = document.getElementById('status-text');
        this.agentNameElement = document.getElementById('agent-name');
        this.agentDescriptionElement = document.getElementById('agent-description');

        // Update agent name in header (initial display)
        this.agentNameElement.textContent = this.formatAgentName(this.agentName);
    },

    async loadAssistantInfo() {
        try {
            const response = await fetch(`/assistants/${this.agentName}`);
            if (!response.ok) {
                console.warn('Failed to load assistant info, using defaults');
                return;
            }

            const data = await response.json();

            // Update header with fetched information
            this.agentNameElement.textContent = this.formatAgentName(data.name);
            if (data.description) {
                this.agentDescriptionElement.textContent = data.description;
            }
        } catch (error) {
            console.error('Error loading assistant info:', error);
        }
    },

    setupEventListeners() {
        // Send button click
        this.sendButton.addEventListener('click', () => this.handleSend());

        // Enter key to send (Shift+Enter for new line)
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.handleSend();
            }
        });

        // Enable/disable send button based on input
        this.messageInput.addEventListener('input', () => {
            const hasText = this.messageInput.value.trim().length > 0;
            this.sendButton.disabled = !hasText || !this.isConnected();
        });

        // Auto-resize textarea
        this.messageInput.addEventListener('input', () => {
            this.messageInput.style.height = 'auto';
            this.messageInput.style.height = this.messageInput.scrollHeight + 'px';
        });
    },

    connectWebSocket() {
        this.wsClient = new WebSocketClient(this.agentName);

        this.wsClient.on('Open', () => {
            this.updateConnectionStatus('connected', 'Connected');
            this.sendButton.disabled = this.messageInput.value.trim().length === 0;
        });

        this.wsClient.on('Close', () => {
            this.updateConnectionStatus('disconnected', 'Disconnected');
            this.sendButton.disabled = true;
        });

        this.wsClient.on('Error', (error) => {
            this.updateConnectionStatus('error', 'Connection Error');
        });

        this.wsClient.on('ReasoningChunk', (chunk) => {
            this.handleReasoningChunk(chunk);
        });

        this.wsClient.on('ToolStatus', (status) => {
            this.handleToolStatus(status);
        });

        this.wsClient.on('FinalResponse', (content) => {
            this.handleFinalResponse(content);
        });

        this.wsClient.on('ErrorMessage', (errorMsg) => {
            this.addErrorMessage(errorMsg);
            this.enableInput();
        });

        this.wsClient.connect();
    },

    handleSend() {
        const query = this.messageInput.value.trim();
        if (!query || !this.isConnected()) {
            return;
        }

        // Get uploaded file info
        const filesInfo = typeof FileUpload !== 'undefined' ? FileUpload.getUploadedFilesInfo() : [];
        const filePaths = filesInfo.map(f => f.path);

        // Add user message to UI with full file info
        this.addUserMessage(query, filesInfo);

        // Send to server with file paths
        this.wsClient.sendMessage(query, null, filePaths);

        // Clear input and disable
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.disableInput();

        // Clear uploaded files after sending
        if (typeof FileUpload !== 'undefined') {
            FileUpload.clearUploadedFiles();
        }

        // Show typing indicator
        this.showTypingIndicator();

        // Reset for new response
        this.currentMessageId = this.generateMessageId();
        this.reasoningChunks = [];
        this.currentToolStatus = null;
    },

    addUserMessage(text, filesInfo = []) {
        const messageDiv = this.createMessageElement('user', text, filesInfo);
        this.chatContainer.appendChild(messageDiv);
        this.scrollToBottom();
    },

    handleReasoningChunk(chunk) {
        this.hideTypingIndicator();
        this.reasoningChunks.push(chunk);

        // Create or update assistant message with reasoning
        let assistantMessage = document.getElementById(this.currentMessageId);
        if (!assistantMessage) {
            assistantMessage = this.createAssistantMessageElement(this.currentMessageId);
            this.chatContainer.appendChild(assistantMessage);
        }

        const contentDiv = assistantMessage.querySelector('.message-content');

        // Ensure reasoning section exists
        let reasoningSection = contentDiv.querySelector('.reasoning-section');
        if (!reasoningSection) {
            reasoningSection = this.createReasoningSection();
            contentDiv.insertBefore(reasoningSection, contentDiv.firstChild);
        }

        const reasoningContent = reasoningSection.querySelector('.reasoning-content');

        // Check if any tool status badges exist inside reasoning-content
        const toolStatusBadges = reasoningContent.querySelectorAll('.tool-status-badge');

        if (toolStatusBadges.length > 0) {
            // Tool calls exist, get or create a reasoning-content-block after the last element
            const lastChild = reasoningContent.lastElementChild;
            let currentBlock;

            if (lastChild && lastChild.classList.contains('tool-status-badge')) {
                // Last element is a badge, create new block
                currentBlock = document.createElement('div');
                currentBlock.className = 'reasoning-content-block';
                reasoningContent.appendChild(currentBlock);
            } else if (lastChild && lastChild.classList.contains('reasoning-content-block')) {
                // Last element is already a block, use it
                currentBlock = lastChild;
            } else {
                // Shouldn't happen, but create a block just in case
                currentBlock = document.createElement('div');
                currentBlock.className = 'reasoning-content-block';
                reasoningContent.appendChild(currentBlock);
            }

            // Update the current block
            const text = this.reasoningChunks.join('');
            currentBlock.innerHTML = this.renderMarkdown(text);
        } else {
            // No tool calls yet, update initial reasoning block
            const initialBlock = reasoningContent.querySelector('.reasoning-content-block');
            if (initialBlock) {
                const text = this.reasoningChunks.join('');
                initialBlock.innerHTML = this.renderMarkdown(text);
            }
        }

        // Auto-scroll reasoning content to bottom
        reasoningContent.scrollTop = reasoningContent.scrollHeight;
        this.scrollToBottom();
    },

    handleToolStatus(status) {
        this.hideTypingIndicator();
        this.currentToolStatus = status;

        // Create or update assistant message with tool status
        let assistantMessage = document.getElementById(this.currentMessageId);
        if (!assistantMessage) {
            assistantMessage = this.createAssistantMessageElement(this.currentMessageId);
            this.chatContainer.appendChild(assistantMessage);
        }

        // Show tool status badge AFTER reasoning (DeepSeek-style: sequential display)
        this.updateToolStatusBadge(assistantMessage, status);
        this.scrollToBottom();
    },

    handleFinalResponse(content) {
        this.hideTypingIndicator();

        let assistantMessage = document.getElementById(this.currentMessageId);
        if (!assistantMessage) {
            assistantMessage = this.createAssistantMessageElement(this.currentMessageId);
            this.chatContainer.appendChild(assistantMessage);
        }

        this.updateFinalResponse(assistantMessage, content);
        this.scrollToBottom();
        this.enableInput();

        // Refresh context gauge after response
        if (typeof ContextGauge !== 'undefined') {
            ContextGauge.refresh();
        }
    },

    createMessageElement(type, text, filesInfo = []) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = type === 'user' ? 'U' : 'A';

        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'message-content-wrapper';

        // Add attached files if present (above the message bubble)
        if (filesInfo.length > 0) {
            const filesDiv = document.createElement('div');
            filesDiv.className = 'message-files';
            filesInfo.forEach(fileInfo => {
                const fileDiv = this.createMessageFileElement(fileInfo);
                filesDiv.appendChild(fileDiv);
            });
            contentWrapper.appendChild(filesDiv);
        }

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        textDiv.textContent = text;

        const timestamp = document.createElement('div');
        timestamp.className = 'message-timestamp';
        timestamp.textContent = this.formatTime(new Date());

        contentDiv.appendChild(textDiv);
        contentDiv.appendChild(timestamp);

        contentWrapper.appendChild(contentDiv);

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentWrapper);

        return messageDiv;
    },

    createMessageFileElement(fileInfo) {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'message-file';

        const icon = document.createElement('span');
        icon.className = 'message-file-extension file-extension-' + fileInfo.extension;
        icon.textContent = fileInfo.extension;

        const infoContainer = document.createElement('div');
        infoContainer.className = 'message-file-info';

        const nameSpan = document.createElement('span');
        nameSpan.className = 'message-file-name';
        nameSpan.textContent = fileInfo.name;
        nameSpan.title = fileInfo.name;

        const sizeSpan = document.createElement('span');
        sizeSpan.className = 'message-file-size';
        sizeSpan.textContent = this.formatFileSize(fileInfo.size);

        infoContainer.appendChild(nameSpan);
        infoContainer.appendChild(sizeSpan);

        fileDiv.appendChild(icon);
        fileDiv.appendChild(infoContainer);

        return fileDiv;
    },

    formatFileSize(bytes) {
        if (bytes === 0 || bytes === null || bytes === undefined) {
            return '0 B';
        }

        const kb = bytes / 1024;
        const mb = kb / 1024;
        const gb = mb / 1024;

        if (gb >= 1) {
            return `${gb.toFixed(gb >= 10 ? 0 : 1)} GB`;
        } else if (mb >= 1) {
            return `${mb.toFixed(mb >= 10 ? 0 : 1)} MB`;
        } else if (kb >= 1) {
            return `${kb.toFixed(kb >= 10 ? 0 : 1)} KB`;
        } else {
            return `${bytes} B`;
        }
    },

    createAssistantMessageElement(id) {
        const messageDiv = document.createElement('div');
        messageDiv.id = id;
        messageDiv.className = 'message assistant';

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = 'A';

        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'message-content-wrapper';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        contentWrapper.appendChild(contentDiv);

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentWrapper);

        return messageDiv;
    },


    createReasoningSection() {
        const section = document.createElement('div');
        section.className = 'reasoning-section';
        section.innerHTML = `
            <div class="reasoning-header">
                <span class="reasoning-toggle expanded">▶</span>
                <span class="reasoning-title">Reasoning</span>
            </div>
            <div class="reasoning-content expanded">
                <div class="reasoning-content-block"></div>
            </div>
        `;

        // Toggle functionality
        const header = section.querySelector('.reasoning-header');
        const toggle = section.querySelector('.reasoning-toggle');
        const content = section.querySelector('.reasoning-content');

        header.addEventListener('click', () => {
            const isExpanded = toggle.classList.toggle('expanded');
            content.classList.toggle('expanded', isExpanded);
        });

        return section;
    },

    clearReasoningSection(messageElement) {
        const contentDiv = messageElement.querySelector('.message-content');
        const reasoningSection = contentDiv.querySelector('.reasoning-section');

        if (reasoningSection) {
            // Add fade-out animation
            reasoningSection.classList.add('fade-out');
            setTimeout(() => {
                reasoningSection.remove();
                this.reasoningChunks = []; // Clear stored chunks
            }, 300); // Match CSS transition duration
        }
    },

    updateToolStatusBadge(messageElement, status) {
        const contentDiv = messageElement.querySelector('.message-content');

        // Ensure reasoning section exists
        let reasoningSection = contentDiv.querySelector('.reasoning-section');
        if (!reasoningSection) {
            reasoningSection = this.createReasoningSection();
            contentDiv.insertBefore(reasoningSection, contentDiv.firstChild);
        }

        const reasoningContent = reasoningSection.querySelector('.reasoning-content');

        // Always create a NEW status badge inside reasoning-content
        const statusBadge = document.createElement('div');
        statusBadge.className = 'tool-status-badge';

        // Determine badge type (calling or completed)
        const isCalling = status.includes('⚙️') || status.toLowerCase().includes('calling');
        const isCompleted = status.includes('✓') || status.toLowerCase().includes('completed');

        if (isCalling) {
            statusBadge.classList.add('calling');
        } else if (isCompleted) {
            statusBadge.classList.add('completed');
        }

        statusBadge.textContent = status;

        // Append to reasoning-content (as sibling to reasoning-content-blocks and other badges)
        reasoningContent.appendChild(statusBadge);

        // Reset reasoning chunks for next post-tool reasoning
        this.reasoningChunks = [];
    },

    updateFinalResponse(messageElement, text) {
        const contentDiv = messageElement.querySelector('.message-content');

        // Keep reasoning expanded - don't collapse
        // Keep status badge visible - don't remove
        // Keep post-tool content visible - don't remove
        // Just add the final answer below everything

        let textDiv = contentDiv.querySelector('.message-text');
        if (!textDiv) {
            textDiv = document.createElement('div');
            textDiv.className = 'message-text';
            contentDiv.appendChild(textDiv);
        }

        // Convert markdown to HTML
        textDiv.innerHTML = this.renderMarkdown(text);

        let timestamp = contentDiv.querySelector('.message-timestamp');
        if (!timestamp) {
            timestamp = document.createElement('div');
            timestamp.className = 'message-timestamp';
            contentDiv.appendChild(timestamp);
        }
        timestamp.textContent = this.formatTime(new Date());
    },

    renderMarkdown(text) {
        if (typeof marked === 'undefined') {
            // Fallback if marked.js not loaded
            return escapeHtml(text).replace(/\n/g, '<br>');
        }

        try {
            // Configure marked options
            marked.setOptions({
                breaks: true,        // Convert \n to <br>
                gfm: true,          // GitHub Flavored Markdown
                headerIds: false,   // Don't add IDs to headers
                mangle: false,      // Don't escape autolinked email addresses
                highlight: (code, lang) => {
                    // Use highlight.js for syntax highlighting if available
                    if (typeof hljs !== 'undefined' && lang) {
                        try {
                            return hljs.highlight(code, {language: lang}).value;
                        } catch (e) {
                            console.warn('Highlight.js error:', e);
                        }
                    }
                    return code;
                }
            });

            const html = marked.parse(text);

            // Apply syntax highlighting to code blocks without language specified
            setTimeout(() => {
                if (typeof hljs !== 'undefined') {
                    document.querySelectorAll('pre code:not(.hljs)').forEach((block) => {
                        hljs.highlightElement(block);
                    });
                }
            }, 0);

            return html;
        } catch (error) {
            console.error('Error rendering markdown:', error);
            return escapeHtml(text);
        }
    },

    showTypingIndicator() {
        if (document.getElementById('typing-indicator')) {
            return; // Already showing
        }

        const indicator = document.createElement('div');
        indicator.id = 'typing-indicator';
        indicator.className = 'message assistant';
        indicator.innerHTML = `
            <div class="message-avatar">A</div>
            <div class="message-content">
                <div class="typing-indicator">
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                    <div class="typing-dot"></div>
                </div>
            </div>
        `;

        this.chatContainer.appendChild(indicator);
        this.scrollToBottom();
    },

    hideTypingIndicator() {
        const indicator = document.getElementById('typing-indicator');
        if (indicator) {
            indicator.remove();
        }
    },

    addErrorMessage(errorText) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = `Error: ${errorText}`;
        this.chatContainer.appendChild(errorDiv);
        this.scrollToBottom();
    },

    updateConnectionStatus(status, text) {
        this.statusIndicator.className = `status-dot ${status}`;
        this.statusText.textContent = text;
    },

    disableInput() {
        this.messageInput.disabled = true;
        this.sendButton.disabled = true;
    },

    enableInput() {
        this.messageInput.disabled = false;
        this.messageInput.focus();
        this.sendButton.disabled = this.messageInput.value.trim().length === 0;
    },

    isConnected() {
        return this.wsClient && this.wsClient.ws &&
            this.wsClient.ws.readyState === WebSocket.OPEN;
    },

    scrollToBottom() {
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    },

    formatTime(date) {
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit'
        });
    },

    formatAgentName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    },

    generateMessageId() {
        return 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }
};
