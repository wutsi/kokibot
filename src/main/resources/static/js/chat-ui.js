/**
 * Chat UI management
 * Handles rendering messages, user interactions, and UI state
 */
const ChatUI = {
    wsClient: null,
    currentMessageId: null,
    reasoningChunks: [],

    init(agentName) {
        this.agentName = agentName || 'thoth';
        this.setupElements();
        this.setupEventListeners();
        this.connectWebSocket();
    },

    setupElements() {
        this.chatContainer = document.getElementById('chat-container');
        this.messageInput = document.getElementById('message-input');
        this.sendButton = document.getElementById('send-button');
        this.statusIndicator = document.getElementById('status-indicator');
        this.statusText = document.getElementById('status-text');
        this.agentNameElement = document.getElementById('agent-name');

        // Update agent name in header
        this.agentNameElement.textContent = this.formatAgentName(this.agentName);
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
            this.addErrorMessage('Failed to connect to server');
        });

        this.wsClient.on('ReasoningChunk', (chunk) => {
            this.handleReasoningChunk(chunk);
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

        // Add user message to UI
        this.addUserMessage(query);

        // Send to server
        this.wsClient.sendMessage(query);

        // Clear input and disable
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.disableInput();

        // Show typing indicator
        this.showTypingIndicator();

        // Reset for new response
        this.currentMessageId = this.generateMessageId();
        this.reasoningChunks = [];
    },

    addUserMessage(text) {
        const messageDiv = this.createMessageElement('user', text);
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

        this.updateReasoningSection(assistantMessage, this.reasoningChunks);
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
    },

    createMessageElement(type, text) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = type === 'user' ? 'U' : 'A';

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

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentDiv);

        return messageDiv;
    },

    createAssistantMessageElement(id) {
        const messageDiv = document.createElement('div');
        messageDiv.id = id;
        messageDiv.className = 'message assistant';

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = 'A';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentDiv);

        return messageDiv;
    },

    updateReasoningSection(messageElement, chunks) {
        const contentDiv = messageElement.querySelector('.message-content');

        let reasoningSection = contentDiv.querySelector('.reasoning-section');
        if (!reasoningSection) {
            reasoningSection = this.createReasoningSection();
            contentDiv.insertBefore(reasoningSection, contentDiv.firstChild);
        }

        const reasoningContent = reasoningSection.querySelector('.reasoning-content');
        const fullText = chunks.join('');
        reasoningContent.innerHTML = `<div class="reasoning-chunk">${this.renderMarkdown(fullText)}</div>`;

        // Auto-scroll to bottom of reasoning box
        reasoningContent.scrollTop = reasoningContent.scrollHeight;
    },

    createReasoningSection() {
        const section = document.createElement('div');
        section.className = 'reasoning-section';
        section.innerHTML = `
            <div class="reasoning-header">
                <span class="reasoning-toggle expanded">▶</span>
                <span class="reasoning-title">Reasoning</span>
            </div>
            <div class="reasoning-content expanded"></div>
        `;

        // Toggle functionality
        const header = section.querySelector('.reasoning-header');
        const content = section.querySelector('.reasoning-content');
        const toggle = section.querySelector('.reasoning-toggle');

        header.addEventListener('click', () => {
            const isExpanded = content.classList.toggle('expanded');
            toggle.classList.toggle('expanded', isExpanded);
        });

        return section;
    },

    updateFinalResponse(messageElement, text) {
        const contentDiv = messageElement.querySelector('.message-content');

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
