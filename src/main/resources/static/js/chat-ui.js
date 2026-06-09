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

    init(agentName) {
        this.agentName = agentName || 'Koki';
        this.setupElements();
        this.initializeComponents();
        this.setupConnectionHandlers();
        this.setupInputHandlers();

        this.assistantInfoLoader.load(agentName);
        this.connectionManager.connect();
    },

    setupElements() {
        this.chatContainer = document.getElementById('chat-container');
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
            this.inputController.enable();
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

        this.connectionManager.on('finalResponse', (content, finishReason, contextLength) => {
            this.handleFinalResponse(content, finishReason, contextLength);
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
        this.connectionManager.sendMessage(text, null, filePaths);

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

    handleFinalResponse(content, finishReason, contextLength) {
        const messageElement = document.getElementById(this.currentMessageId);
        if (!messageElement) {
            console.error('Assistant message not found for final response');
            return;
        }

        this.messageRenderer.updateFinalResponse(messageElement, content);
        this.inputController.enable();

        if (typeof ContextGauge !== 'undefined' && contextLength !== null && contextLength !== undefined) {
            ContextGauge.updateContextLength(contextLength);
        }
    },

    updateConnectionStatus(status, text) {
        this.statusIndicator.className = `status-dot ${status}`;
        this.statusText.textContent = text;
    },

    generateMessageId() {
        return 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }
};
