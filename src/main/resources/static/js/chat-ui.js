/**
 * Chat UI management (Refactored)
 * Orchestrator that delegates to specialized components
 */
const ChatUI = {
    connectionManager: null,
    messageRenderer: null,
    reasoningView: null,
    tokenDisplay: null,
    inputController: null,
    assistantInfoLoader: null,

    agentName: null,
    currentMessageId: null,
    chatContainer: null,
    messageInput: null,
    sendButton: null,
    statusIndicator: null,
    statusText: null,
    agentNameElement: null,
    agentDescriptionElement: null,
    conversationId: null,
    historyLoaded: false,

    init(agentName) {
        this.agentName = agentName || 'Koki';
        this.conversationId = null;
        this.historyLoaded = false;
        this.setupElements();
        this.initializeComponents();
        this.setupConnectionHandlers();
        this.setupInputHandlers();

        this.assistantInfoLoader.load(agentName);
        this.connectionManager.connect();
    },

    async loadConversationHistory() {
        const params = new URLSearchParams(window.location.search);
        const convFromURL = params.get('conv');
        const storedId = convFromURL || localStorage.getItem(`kokibot_conv_${this.agentName}`);
        if (!storedId) return;

        if (convFromURL) {
            localStorage.setItem(`kokibot_conv_${this.agentName}`, convFromURL);
            params.delete('conv');
            history.replaceState(null, '', '/index.html?' + params.toString());
        }

        this.conversationId = storedId;

        const placeholder = document.createElement('div');
        placeholder.id = 'history-loading';
        placeholder.className = 'history-loading';
        placeholder.textContent = 'Loading conversation…';
        this.chatContainer.appendChild(placeholder);

        try {
            const response = await fetch(
                `/assistants/${this.agentName}/conversations/${storedId}`
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const detail = await response.json();
            placeholder.remove();

            for (const message of detail.messages) {
                if (message.role === 'user') {
                    const filesInfo = (message.files || []).map(path => {
                        const name = path.split('/').pop();
                        const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
                        return { path, name, extension: ext, size: 0 };
                    });
                    this.messageRenderer.addUserMessage(message.text, filesInfo);
                } else if (message.role === 'assistant') {
                    this.messageRenderer.addAssistantMessage(message.text);
                }
            }

            ConversationHistory.setActiveConversation(this.conversationId);
        } catch (e) {
            console.warn('Failed to load conversation history:', e);
            placeholder.remove();
            Notifications.error('Failed to load conversation history. Reload the page to try again.', { duration: 0 });
        }
    },

    newChat() {
        localStorage.removeItem(`kokibot_conv_${this.agentName}`);
        this.conversationId = null;
        this.historyLoaded = true;
        this.chatContainer.innerHTML = '';
        ContextWindowDisplay.reset();
    },

    setupElements() {
        this.chatContainer = document.getElementById('chat-container');
        if (!this.chatContainer) throw new Error('Required element #chat-container not found');
        this.messageInput = document.getElementById('message-input');
        this.sendButton = document.getElementById('send-button');
        this.statusIndicator = document.getElementById('status-indicator');
        this.statusText = document.getElementById('status-text');
        this.agentNameElement = document.getElementById('agent-name');
        this.agentDescriptionElement = document.getElementById('agent-description');
    },

    initializeComponents() {
        this.connectionManager = new ConnectionManager(this.agentName);
        this.messageRenderer = new MessageRenderer(this.chatContainer);
        this.reasoningView = new ReasoningView();
        this.tokenDisplay = new TokenDisplay();
        this.inputController = new InputController(this.messageInput, this.sendButton);
        this.assistantInfoLoader = new AssistantInfoLoader(
            this.agentNameElement,
            this.agentDescriptionElement
        );
    },

    setupConnectionHandlers() {
        this.connectionManager.on('open', () => {
            this.updateConnectionStatus('connected', 'Connected');
            ContextWindowDisplay.refresh();
            if (!this.historyLoaded) {
                this.loadConversationHistory().then(() => {
                    this.historyLoaded = true;
                    this.inputController.enable();
                    ContextWindowDisplay.refresh(this.conversationId);
                });
            } else {
                this.inputController.enable();
            }
        });

        this.connectionManager.on('close', () => {
            this.updateConnectionStatus('disconnected', 'Disconnected');
            this.inputController.disable();
        });

        this.connectionManager.on('error', (error) => {
            this.updateConnectionStatus('error', 'Connection Error');
        });

        this.connectionManager.on('reasoningChunk', (chunk, usage) => {
            this.handleReasoningChunk(chunk, usage);
        });

        this.connectionManager.on('toolStatus', (status) => {
            this.handleToolStatus(status);
        });

        this.connectionManager.on('finalResponse', (content, finishReason, conversationId) => {
            this.handleFinalResponse(content, finishReason, conversationId);
        });
    },

    setupInputHandlers() {
        this.inputController.on('send', (text, filesInfo) => {
            this.handleSend(text, filesInfo);
        });
    },

    handleSend(text, filesInfo) {
        this.messageRenderer.addUserMessage(text, filesInfo);

        const filePaths = filesInfo.map(f => f.path);
        this.connectionManager.sendMessage(text, filePaths, this.conversationId);

        this.inputController.disable();

        this.currentMessageId = this.generateMessageId();
        this.messageRenderer.createAssistantMessage(this.currentMessageId);

        this.reasoningView.reset();
        this.tokenDisplay.reset();
    },

    handleReasoningChunk(chunk, usage) {
        const messageElement = document.getElementById(this.currentMessageId);
        if (!messageElement) {
            console.error('Assistant message not found for reasoning chunk');
            return;
        }

        this.reasoningView.appendChunk(messageElement, chunk);

        if (usage && usage.totalTokens > 0) {
            this.tokenDisplay.update(messageElement, usage);
        }

        this.messageRenderer.scrollToBottom();
    },

    handleToolStatus(status) {
        const messageElement = document.getElementById(this.currentMessageId);
        if (!messageElement) {
            console.error('Assistant message not found for tool status');
            return;
        }

        this.reasoningView.addToolStatus(messageElement, status);
        this.messageRenderer.scrollToBottom();
    },

    handleFinalResponse(content, finishReason, conversationId) {
        const messageElement = document.getElementById(this.currentMessageId);
        if (!messageElement) {
            console.error('Assistant message not found for final response');
            return;
        }

        this.messageRenderer.updateFinalResponse(messageElement, content);
        this.inputController.enable();

        if (conversationId) {
            this.conversationId = conversationId;
            localStorage.setItem(`kokibot_conv_${this.agentName}`, conversationId);
            ConversationHistory.setActiveConversation(conversationId);
        }
        ContextWindowDisplay.refresh(this.conversationId);
    },

    updateConnectionStatus(status, text) {
        this.statusIndicator.className = `status-dot ${status}`;
        this.statusText.textContent = text;
    },

    generateMessageId() {
        return 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }
};
