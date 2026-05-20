# WebSocket Web Client Implementation Plan

**Date:** 2026-05-19  
**Status:** Planning  
**Objective:** Create a Gemini-style web interface for interacting with Kokibot agents via WebSocket

## Overview

Build a modern, responsive web client that provides a chat-style interface for querying Kokibot agents. The UI should follow Google Gemini's design pattern: clean, minimal, with real-time streaming of responses.

## UI Design Pattern (Gemini-Style)

### Layout Structure

```
┌─────────────────────────────────────────────────────────┐
│  Header: Agent Name                            [Status] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Chat Container (Scrollable)                            │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ User: What is quantum computing?                │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Assistant:                                      │   │
│  │ ┌─ Reasoning (collapsible) ──────────────────┐ │   │
│  │ │ Thinking about the question...             │ │   │
│  │ │ Analyzing key concepts...                  │ │   │
│  │ └────────────────────────────────────────────┘ │   │
│  │                                                 │   │
│  │ Quantum computing is a type of computation... │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  Input Box: [Type your message...]       [Send Button] │
└─────────────────────────────────────────────────────────┘
```

### Visual Design Elements

**Colors (Gemini-inspired):**
- Background: `#f8f9fa` (light gray)
- User messages: `#e3f2fd` (light blue)
- Assistant messages: `#ffffff` (white)
- Reasoning section: `#f5f5f5` (subtle gray)
- Accent color: `#1a73e8` (blue)
- Text: `#202124` (dark gray)

**Typography:**
- Font family: `'Google Sans', 'Roboto', sans-serif`
- User message: 14px, regular
- Assistant message: 15px, regular
- Reasoning: 13px, italic

**Spacing:**
- Message padding: 16px
- Message margin: 12px
- Border radius: 12px
- Max width: 800px (centered)

**Animations:**
- Smooth scroll to new messages
- Fade-in for new messages
- Typing indicator while streaming
- Pulse animation for "thinking" state

## Features

### Core Features (MVP)

1. **WebSocket Connection**
   - Connect to agent-specific endpoint on page load
   - Show connection status (connected/disconnected/error)
   - Auto-reconnect on disconnect
   - Handle connection errors gracefully

2. **Message Input**
   - Text input field at bottom
   - Send button (enabled only when input has text)
   - Send on Enter key (Shift+Enter for new line)
   - Clear input after sending
   - Disable input while processing

3. **Message Display**
   - User messages aligned right
   - Assistant messages aligned left
   - Timestamp for each message
   - Avatar/icon for user and assistant

4. **Real-Time Streaming**
   - Show reasoning chunks as they arrive
   - Stream final response word-by-word (or chunk-by-chunk)
   - Typing indicator while waiting
   - Auto-scroll to latest message

5. **Reasoning Section**
   - Collapsible section for reasoning chunks
   - Collapsed by default (can expand to see thinking process)
   - Visual indicator (▶/▼) for expand/collapse
   - Different background color to distinguish from final answer

6. **Error Handling**
   - Display error messages in chat
   - Show connection errors
   - Retry mechanism for failed requests

### Nice-to-Have Features (Future)

- Clear chat history button
- Copy message to clipboard
- Download chat as text/markdown
- Dark mode toggle
- Select different agents from dropdown
- Voice input
- Markdown rendering in messages
- Code syntax highlighting
- Rate limiting feedback
- Character/token counter

## File Structure

```
src/main/resources/static/
├── index.html                          # Main HTML page
├── css/
│   └── chat.css                        # Styles (Gemini-inspired)
├── js/
│   ├── websocket-client.js             # WebSocket connection management
│   ├── chat-ui.js                      # UI rendering and interactions
│   └── utils.js                        # Helper functions
└── assets/
    ├── user-avatar.svg                 # User avatar icon
    └── assistant-avatar.svg            # Assistant avatar icon
```

## Implementation Steps

### Phase 1: Basic HTML Structure

**File:** `src/main/resources/static/index.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kokibot Chat</title>
    <link rel="stylesheet" href="css/chat.css">
</head>
<body>
    <div class="app-container">
        <!-- Header -->
        <header class="chat-header">
            <div class="agent-info">
                <h1 id="agent-name">Kokibot</h1>
                <span id="agent-description">AI Assistant</span>
            </div>
            <div class="connection-status">
                <span id="status-indicator" class="status-dot"></span>
                <span id="status-text">Connecting...</span>
            </div>
        </header>

        <!-- Chat Container -->
        <main class="chat-container" id="chat-container">
            <!-- Messages will be dynamically inserted here -->
        </main>

        <!-- Input Area -->
        <footer class="input-container">
            <div class="input-wrapper">
                <textarea 
                    id="message-input" 
                    placeholder="Ask me anything..."
                    rows="1"
                    maxlength="10000"
                ></textarea>
                <button id="send-button" disabled>
                    <svg width="24" height="24" viewBox="0 0 24 24">
                        <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                    </svg>
                </button>
            </div>
        </footer>
    </div>

    <script src="js/utils.js"></script>
    <script src="js/websocket-client.js"></script>
    <script src="js/chat-ui.js"></script>
    <script>
        // Initialize app on page load
        document.addEventListener('DOMContentLoaded', () => {
            const agentName = getAgentNameFromURL(); // e.g., ?agent=my-agent
            ChatUI.init(agentName);
        });
    </script>
</body>
</html>
```

### Phase 2: CSS Styling (Gemini-Style)

**File:** `src/main/resources/static/css/chat.css`

```css
/* ===== Reset & Base Styles ===== */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Google Sans', 'Roboto', -apple-system, BlinkMacSystemFont, sans-serif;
    background-color: #f8f9fa;
    color: #202124;
    line-height: 1.6;
}

/* ===== App Container ===== */
.app-container {
    display: flex;
    flex-direction: column;
    height: 100vh;
    max-width: 1200px;
    margin: 0 auto;
    background-color: #ffffff;
    box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);
}

/* ===== Header ===== */
.chat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 24px;
    background-color: #ffffff;
    border-bottom: 1px solid #e8eaed;
}

.agent-info h1 {
    font-size: 20px;
    font-weight: 500;
    color: #202124;
    margin-bottom: 4px;
}

.agent-info span {
    font-size: 13px;
    color: #5f6368;
}

.connection-status {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #5f6368;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #9aa0a6;
    animation: pulse 2s infinite;
}

.status-dot.connected {
    background-color: #34a853;
    animation: none;
}

.status-dot.disconnected {
    background-color: #ea4335;
}

@keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
}

/* ===== Chat Container ===== */
.chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background-color: #f8f9fa;
    scroll-behavior: smooth;
}

/* ===== Messages ===== */
.message {
    display: flex;
    margin-bottom: 24px;
    animation: fadeIn 0.3s ease-in;
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}

.message.user {
    flex-direction: row-reverse;
}

.message-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background-color: #1a73e8;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-weight: 500;
    flex-shrink: 0;
}

.message.assistant .message-avatar {
    background-color: #34a853;
}

.message-content {
    max-width: 70%;
    margin: 0 12px;
}

.message.user .message-content {
    background-color: #e3f2fd;
    border-radius: 18px 18px 4px 18px;
    padding: 12px 16px;
}

.message.assistant .message-content {
    background-color: #ffffff;
    border-radius: 18px 18px 18px 4px;
    padding: 16px;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-text {
    font-size: 15px;
    line-height: 1.6;
    word-wrap: break-word;
}

.message.user .message-text {
    font-size: 14px;
}

.message-timestamp {
    font-size: 11px;
    color: #5f6368;
    margin-top: 4px;
}

/* ===== Reasoning Section ===== */
.reasoning-section {
    margin-bottom: 12px;
    border: 1px solid #e8eaed;
    border-radius: 8px;
    overflow: hidden;
}

.reasoning-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background-color: #f8f9fa;
    cursor: pointer;
    user-select: none;
}

.reasoning-header:hover {
    background-color: #f1f3f4;
}

.reasoning-toggle {
    font-size: 12px;
    transition: transform 0.2s;
}

.reasoning-toggle.expanded {
    transform: rotate(90deg);
}

.reasoning-title {
    font-size: 13px;
    font-weight: 500;
    color: #5f6368;
}

.reasoning-content {
    padding: 12px;
    background-color: #fafafa;
    font-size: 13px;
    color: #5f6368;
    font-style: italic;
    line-height: 1.5;
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease-out;
}

.reasoning-content.expanded {
    max-height: 500px;
    overflow-y: auto;
}

.reasoning-chunk {
    margin-bottom: 8px;
}

/* ===== Typing Indicator ===== */
.typing-indicator {
    display: flex;
    gap: 4px;
    padding: 12px 16px;
}

.typing-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #9aa0a6;
    animation: typing 1.4s infinite;
}

.typing-dot:nth-child(2) {
    animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
    animation-delay: 0.4s;
}

@keyframes typing {
    0%, 60%, 100% { transform: translateY(0); }
    30% { transform: translateY(-10px); }
}

/* ===== Input Container ===== */
.input-container {
    padding: 16px 24px;
    background-color: #ffffff;
    border-top: 1px solid #e8eaed;
}

.input-wrapper {
    display: flex;
    gap: 12px;
    align-items: flex-end;
    max-width: 800px;
    margin: 0 auto;
}

#message-input {
    flex: 1;
    padding: 12px 16px;
    border: 1px solid #e8eaed;
    border-radius: 24px;
    font-size: 14px;
    font-family: inherit;
    resize: none;
    outline: none;
    transition: border-color 0.2s;
    max-height: 200px;
    overflow-y: auto;
}

#message-input:focus {
    border-color: #1a73e8;
}

#send-button {
    width: 48px;
    height: 48px;
    border: none;
    border-radius: 50%;
    background-color: #1a73e8;
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background-color 0.2s, opacity 0.2s;
}

#send-button:hover:not(:disabled) {
    background-color: #1557b0;
}

#send-button:disabled {
    background-color: #e8eaed;
    cursor: not-allowed;
    opacity: 0.5;
}

#send-button svg {
    fill: currentColor;
}

/* ===== Error Message ===== */
.error-message {
    background-color: #fce8e6;
    border-left: 4px solid #ea4335;
    padding: 12px 16px;
    margin-bottom: 16px;
    border-radius: 4px;
    font-size: 14px;
    color: #c5221f;
}

/* ===== Responsive Design ===== */
@media (max-width: 768px) {
    .chat-container {
        padding: 16px;
    }

    .message-content {
        max-width: 85%;
    }

    .input-container {
        padding: 12px 16px;
    }
}

/* ===== Scrollbar Styling ===== */
.chat-container::-webkit-scrollbar {
    width: 8px;
}

.chat-container::-webkit-scrollbar-track {
    background: #f1f3f4;
}

.chat-container::-webkit-scrollbar-thumb {
    background: #dadce0;
    border-radius: 4px;
}

.chat-container::-webkit-scrollbar-thumb:hover {
    background: #bdc1c6;
}
```

### Phase 3: WebSocket Client Logic

**File:** `src/main/resources/static/js/websocket-client.js`

```javascript
/**
 * WebSocket client for Kokibot
 * Handles connection, sending/receiving messages, and reconnection logic
 */
class WebSocketClient {
    constructor(agentName) {
        this.agentName = agentName;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 1000; // Start with 1 second
        this.messageQueue = [];
        this.handlers = {
            onOpen: null,
            onClose: null,
            onError: null,
            onReasoningChunk: null,
            onFinalResponse: null,
            onErrorMessage: null
        };
    }

    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.host;
        const path = `/ws/${this.agentName}`;
        const wsUrl = `${protocol}//${host}${path}`;

        console.log(`Connecting to WebSocket: ${wsUrl}`);

        try {
            this.ws = new WebSocket(wsUrl);
            this.setupEventHandlers();
        } catch (error) {
            console.error('WebSocket connection error:', error);
            this.handleError(error);
        }
    }

    setupEventHandlers() {
        this.ws.onopen = (event) => {
            console.log('WebSocket connected');
            this.reconnectAttempts = 0;
            this.reconnectDelay = 1000;
            
            if (this.handlers.onOpen) {
                this.handlers.onOpen(event);
            }

            // Send queued messages
            this.flushMessageQueue();
        };

        this.ws.onmessage = (event) => {
            try {
                const response = JSON.parse(event.data);
                this.handleMessage(response);
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
            }
        };

        this.ws.onerror = (event) => {
            console.error('WebSocket error:', event);
            this.handleError(event);
        };

        this.ws.onclose = (event) => {
            console.log('WebSocket closed:', event.code, event.reason);
            
            if (this.handlers.onClose) {
                this.handlers.onClose(event);
            }

            // Attempt reconnection
            this.attemptReconnect();
        };
    }

    handleMessage(response) {
        switch (response.type) {
            case 'REASONING_CHUNK':
                if (this.handlers.onReasoningChunk) {
                    this.handlers.onReasoningChunk(response.content);
                }
                break;

            case 'FINAL':
                if (this.handlers.onFinalResponse) {
                    this.handlers.onFinalResponse(response.content, response.finishReason);
                }
                break;

            case 'ERROR':
                if (this.handlers.onErrorMessage) {
                    this.handlers.onErrorMessage(response.message);
                }
                break;

            default:
                console.warn('Unknown message type:', response.type);
        }
    }

    sendMessage(query, userId = null) {
        const message = {
            query: query,
            userId: userId || this.generateUserId()
        };

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.warn('WebSocket not open, queueing message');
            this.messageQueue.push(message);
            
            // Try to reconnect if not connected
            if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
                this.connect();
            }
        }
    }

    flushMessageQueue() {
        while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            this.ws.send(JSON.stringify(message));
        }
    }

    attemptReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        this.reconnectAttempts++;
        console.log(`Reconnecting in ${this.reconnectDelay}ms (attempt ${this.reconnectAttempts})`);

        setTimeout(() => {
            this.connect();
        }, this.reconnectDelay);

        // Exponential backoff
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
    }

    handleError(error) {
        if (this.handlers.onError) {
            this.handlers.onError(error);
        }
    }

    generateUserId() {
        // Get or create user ID from localStorage
        let userId = localStorage.getItem('kokibot_user_id');
        if (!userId) {
            userId = 'user_' + Math.random().toString(36).substring(2, 15);
            localStorage.setItem('kokibot_user_id', userId);
        }
        return userId;
    }

    disconnect() {
        if (this.ws) {
            this.ws.close();
        }
    }

    on(event, handler) {
        if (this.handlers.hasOwnProperty(`on${event}`)) {
            this.handlers[`on${event}`] = handler;
        }
    }
}
```

### Phase 4: Chat UI Management

**File:** `src/main/resources/static/js/chat-ui.js`

```javascript
/**
 * Chat UI management
 * Handles rendering messages, user interactions, and UI state
 */
const ChatUI = {
    wsClient: null,
    currentMessageId: null,
    reasoningChunks: [],

    init(agentName) {
        this.agentName = agentName || 'default';
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
        reasoningContent.innerHTML = chunks.map(chunk => 
            `<div class="reasoning-chunk">${this.escapeHtml(chunk)}</div>`
        ).join('');
    },

    createReasoningSection() {
        const section = document.createElement('div');
        section.className = 'reasoning-section';
        section.innerHTML = `
            <div class="reasoning-header">
                <span class="reasoning-toggle">▶</span>
                <span class="reasoning-title">View reasoning</span>
            </div>
            <div class="reasoning-content"></div>
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

        textDiv.textContent = text;

        let timestamp = contentDiv.querySelector('.message-timestamp');
        if (!timestamp) {
            timestamp = document.createElement('div');
            timestamp.className = 'message-timestamp';
            contentDiv.appendChild(timestamp);
        }
        timestamp.textContent = this.formatTime(new Date());
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
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
```

### Phase 5: Utility Functions

**File:** `src/main/resources/static/js/utils.js`

```javascript
/**
 * Utility functions
 */

function getAgentNameFromURL() {
    const params = new URLSearchParams(window.location.search);
    return params.get('agent') || 'default';
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function throttle(func, limit) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}
```

### Phase 6: Spring Boot Static Resource Configuration

**File:** `src/main/kotlin/com/wutsi/kokibot/config/WebConfiguration.kt` (NEW)

```kotlin
package com.wutsi.kokibot.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfiguration : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Serve static resources from /static
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Map root to index.html
        registry.addViewController("/").setViewName("forward:/index.html")
    }
}
```

## Testing Strategy

### Manual Testing

1. **Connection Testing**
   - Open browser to `http://localhost:8080/?agent=my-agent`
   - Verify connection status shows "Connected"
   - Verify agent name appears in header

2. **Message Flow Testing**
   - Send simple query "Hello"
   - Verify user message appears on right
   - Verify typing indicator shows
   - Verify reasoning chunks appear in collapsible section
   - Verify final response appears
   - Verify timestamps are correct

3. **Streaming Testing**
   - Send complex query requiring reasoning
   - Verify reasoning chunks stream in real-time
   - Verify reasoning section is collapsible
   - Verify final answer appears after reasoning

4. **Error Testing**
   - Disconnect WebSocket server
   - Verify "Disconnected" status
   - Verify error message appears in chat
   - Reconnect server and verify auto-reconnection

5. **UI/UX Testing**
   - Test on different screen sizes (mobile, tablet, desktop)
   - Test keyboard shortcuts (Enter to send, Shift+Enter for new line)
   - Test auto-scroll behavior
   - Test input field auto-resize
   - Test send button enable/disable logic

6. **Multi-Agent Testing**
   - Open multiple tabs with different agents
   - Verify each connects to correct endpoint
   - Verify messages don't cross between agents

### Browser Compatibility

Test on:
- Chrome/Edge (Chromium)
- Firefox
- Safari

## Deployment

### Development

```bash
# Run Spring Boot application
mvn spring-boot:run

# Open browser
open http://localhost:8080/?agent=my-agent
```

### Production Considerations

1. **Security**
   - Add CORS configuration for allowed origins
   - Add authentication/authorization if needed
   - Sanitize user input before sending to WebSocket
   - Implement rate limiting

2. **Performance**
   - Minify CSS/JS files
   - Enable gzip compression
   - Add CDN for static assets
   - Implement client-side caching

3. **Monitoring**
   - Add analytics tracking (page views, message counts)
   - Log WebSocket connection errors
   - Monitor reconnection attempts

## Success Criteria

- [ ] Page loads successfully at `http://localhost:8080/`
- [ ] WebSocket connection established to agent-specific endpoint
- [ ] User can send messages via input field
- [ ] Messages display in chat-style layout
- [ ] Reasoning chunks stream in real-time
- [ ] Reasoning section is collapsible
- [ ] Final responses display correctly
- [ ] Connection status updates correctly
- [ ] Auto-reconnection works after disconnect
- [ ] Error messages display in chat
- [ ] UI is responsive on mobile/tablet/desktop
- [ ] Send button disabled when input empty or disconnected
- [ ] Auto-scroll keeps latest message visible
- [ ] Matches Gemini's visual style and UX patterns

## Future Enhancements

### Phase 2 Features

- [ ] **Markdown rendering** - Render markdown in assistant responses
- [ ] **Code syntax highlighting** - Highlight code blocks
- [ ] **Copy to clipboard** - Button to copy messages
- [ ] **Clear chat** - Button to clear conversation history
- [ ] **Download chat** - Export conversation as text/JSON
- [ ] **Dark mode** - Toggle between light/dark themes
- [ ] **Agent selector** - Dropdown to switch between agents
- [ ] **Message editing** - Edit and resend previous messages
- [ ] **Stop generation** - Cancel ongoing response
- [ ] **Voice input** - Speech-to-text for queries

### Phase 3 Features

- [ ] **Multi-user chat** - Multiple users in same conversation
- [ ] **Chat history persistence** - Save/load conversations
- [ ] **File upload UI** - Upload files with queries
- [ ] **Rich media support** - Display images, PDFs in chat
- [ ] **Conversation branching** - Fork conversations at any message
- [ ] **Prompt templates** - Saved templates for common queries
- [ ] **Keyboard shortcuts** - Power user features

## Dependencies

**Frontend:**
- No external JavaScript libraries required (vanilla JS)
- Modern browser with WebSocket support
- ES6+ JavaScript features

**Backend:**
- Spring Boot web starter (already included)
- WebSocket support (already implemented)

## File Checklist

**Create:**
- [ ] `src/main/resources/static/index.html`
- [ ] `src/main/resources/static/css/chat.css`
- [ ] `src/main/resources/static/js/websocket-client.js`
- [ ] `src/main/resources/static/js/chat-ui.js`
- [ ] `src/main/resources/static/js/utils.js`
- [ ] `src/main/kotlin/com/wutsi/kokibot/config/WebConfiguration.kt`

**Optional:**
- [ ] `src/main/resources/static/assets/user-avatar.svg`
- [ ] `src/main/resources/static/assets/assistant-avatar.svg`
- [ ] `src/main/resources/static/favicon.ico`

---

**End of Implementation Plan**
