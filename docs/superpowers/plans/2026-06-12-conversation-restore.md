# Conversation Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store the active `conversationId` in localStorage, restore conversation history on page load via the REST API, and provide a "New Chat" button to reset state.

**Architecture:** Thread `conversationId` from the WebSocket `FINAL` response down through `WebSocketClient` → `ConnectionManager` → `ChatUI`, persist to `localStorage`, and re-send it on the next message. On `ChatUI.init()`, fetch the stored conversation via `GET /assistants/{name}/conversations/{id}?userId=anonymous` and render each message using existing `MessageRenderer` methods.

**Tech Stack:** Vanilla JS (no build step, no test framework), Spring Boot serving static files, existing `WebSocketClient` / `ConnectionManager` / `ChatUI` / `MessageRenderer` / `Sidebar` components.

---

## File Map

### Modified files
| File | Change |
|---|---|
| `static/js/components/message-renderer.js` | Add `addAssistantMessage(text)` for history rendering |
| `static/js/websocket-client.js` | Add `conversationId` param to `sendMessage()`; pass it from `FINAL` handler |
| `static/js/components/connection-manager.js` | Add `conversationId` to `sendMessage()` and `finalResponse` event |
| `static/js/chat-ui.js` | Add `conversationId` field, `loadConversationHistory()`, `newChat()`; update `handleSend()`, `handleFinalResponse()`, `init()`, `setupConnectionHandlers()` |
| `static/index.html` | Add `#new-chat-btn` button to sidebar |
| `static/js/components/sidebar.js` | Wire `#new-chat-btn` → `ChatUI.newChat()` |

---

## Task 1: Add `addAssistantMessage()` to `MessageRenderer`

**Files:**
- Modify: `src/main/resources/static/js/components/message-renderer.js`

- [ ] **Step 1: Add the method**

After the `addUserMessage` method (line 16), insert:

```js
/**
 * Add a complete (non-streaming) assistant message — used for history rendering
 */
addAssistantMessage(text) {
    const id = 'hist_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    const element = this.createAssistantMessage(id);
    this.updateFinalResponse(element, text);
    return element;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/js/components/message-renderer.js
git commit -m "feat: add addAssistantMessage() to MessageRenderer for history rendering"
```

---

## Task 2: Wire `conversationId` through `WebSocketClient`

**Files:**
- Modify: `src/main/resources/static/js/websocket-client.js`

- [ ] **Step 1: Update `sendMessage()` to accept and include `conversationId`**

Replace the current `sendMessage` method:

```js
sendMessage(query, filePaths = [], conversationId = null) {
    const message = {
        query: query,
        filePaths: filePaths,
        conversationId: conversationId,
    };

    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try {
            this.ws.send(JSON.stringify(message));
        } catch (error) {
            console.error('Error sending message:', error);
            this.handleSendFailure(message, error);
        }
    } else {
        console.warn('WebSocket not open, queueing message');
        this.messageQueue.push(message);

        Notifications.warning(
            'Connection unavailable. Your message will be sent when reconnected.',
            { duration: 3000 }
        );

        if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
            this.connect();
        }
    }
}
```

- [ ] **Step 2: Update `handleMessage()` FINAL case to pass `conversationId`**

Replace the `FINAL` case in `handleMessage`:

```js
case 'FINAL':
    if (this.handlers.onFinalResponse) {
        this.handlers.onFinalResponse(response.content, response.finishReason, response.conversationId);
    }
    break;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/websocket-client.js
git commit -m "feat: thread conversationId through WebSocketClient send and receive"
```

---

## Task 3: Wire `conversationId` through `ConnectionManager`

**Files:**
- Modify: `src/main/resources/static/js/components/connection-manager.js`

- [ ] **Step 1: Update `sendMessage()` to accept and forward `conversationId`**

Replace the `sendMessage` method:

```js
sendMessage(query, filePaths = [], conversationId = null) {
    this.wsClient.sendMessage(query, filePaths, conversationId);
}
```

- [ ] **Step 2: Update `FinalResponse` handler to forward `conversationId`**

Replace the `FinalResponse` binding in `connect()`:

```js
this.wsClient.on('FinalResponse', (content, finishReason, conversationId) => {
    this.emit('finalResponse', content, finishReason, conversationId);
});
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/components/connection-manager.js
git commit -m "feat: thread conversationId through ConnectionManager send and receive"
```

---

## Task 4: Update `ChatUI` — store, restore, send, and new chat

**Files:**
- Modify: `src/main/resources/static/js/chat-ui.js`

- [ ] **Step 1: Add `conversationId` field and update `init()`**

Replace the existing `init` method and add the `conversationId` field to the top of the `ChatUI` object:

```js
const ChatUI = {
    connectionManager: null,
    messageRenderer: null,
    reasoningView: null,
    tokenDisplay: null,
    inputController: null,
    assistantInfoLoader: null,

    agentName: null,
    conversationId: null,          // <-- new field
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
        this.conversationId = null;
        this.setupElements();
        this.initializeComponents();
        this.setupConnectionHandlers();
        this.setupInputHandlers();

        this.assistantInfoLoader.load(agentName);
        this.connectionManager.connect();
        this.loadConversationHistory();
    },
```

- [ ] **Step 2: Add `loadConversationHistory()` method**

After the `init` method, add:

```js
async loadConversationHistory() {
    const storedId = localStorage.getItem(`kokibot_conv_${this.agentName}`);
    if (!storedId) return;

    this.conversationId = storedId;

    const placeholder = document.createElement('div');
    placeholder.id = 'history-loading';
    placeholder.className = 'history-loading';
    placeholder.textContent = 'Loading conversation…';
    this.chatContainer.appendChild(placeholder);

    try {
        const response = await fetch(
            `/assistants/${this.agentName}/conversations/${storedId}?userId=anonymous`
        );
        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const detail = await response.json();
        placeholder.remove();

        for (const message of detail.messages) {
            if (message.role === 'user') {
                this.messageRenderer.addUserMessage(message.text);
            } else if (message.role === 'assistant') {
                this.messageRenderer.addAssistantMessage(message.text);
            }
        }
    } catch (e) {
        console.warn('Failed to load conversation history:', e);
        placeholder.remove();
        localStorage.removeItem(`kokibot_conv_${this.agentName}`);
        this.conversationId = null;
    }
},
```

- [ ] **Step 3: Add `newChat()` method**

After `loadConversationHistory`, add:

```js
newChat() {
    localStorage.removeItem(`kokibot_conv_${this.agentName}`);
    this.conversationId = null;
    this.chatContainer.innerHTML = '';
},
```

- [ ] **Step 4: Update `setupConnectionHandlers()` to receive `conversationId`**

Replace the `finalResponse` handler registration:

```js
this.connectionManager.on('finalResponse', (content, finishReason, conversationId) => {
    this.handleFinalResponse(content, finishReason, conversationId);
});
```

- [ ] **Step 5: Update `handleSend()` to pass `conversationId`**

Replace the `connectionManager.sendMessage` call inside `handleSend`:

```js
this.connectionManager.sendMessage(text, filePaths, this.conversationId);
```

- [ ] **Step 6: Update `handleFinalResponse()` to save `conversationId`**

Replace the full `handleFinalResponse` method:

```js
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
    }
},
```

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/chat-ui.js
git commit -m "feat: store and restore conversationId in ChatUI; add newChat()"
```

---

## Task 5: Add New Chat button to HTML and wire in Sidebar

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/components/sidebar.js`

- [ ] **Step 1: Add the button to `index.html`**

In the `<nav class="sidebar-nav">` block, insert the New Chat button **before** the existing `#history-btn`:

```html
<button id="new-chat-btn" class="sidebar-nav-item">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
        <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
    </svg>
    <span>New Chat</span>
</button>
```

- [ ] **Step 2: Update `sidebar.js` to wire the button**

Replace `setupElements()`:

```js
setupElements() {
    this.sidebar = document.getElementById('sidebar');
    this.toggleButton = document.getElementById('sidebar-toggle');
    this.newChatButton = document.getElementById('new-chat-btn');
    this.historyButton = document.getElementById('history-btn');
    this.settingsButton = document.getElementById('settings-btn');
},
```

In `setupEventListeners()`, add after the toggle handler:

```js
if (this.newChatButton) {
    this.newChatButton.addEventListener('click', () => {
        ChatUI.newChat();
    });
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html \
        src/main/resources/static/js/components/sidebar.js
git commit -m "feat: add New Chat button to sidebar"
```

---

## Task 6: End-to-end verification

- [ ] **Step 1: Run the full Kotlin test suite to catch any regressions**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 2: Start the app**

```bash
mvn spring-boot:run
```
Open `http://localhost:8080` in a browser (append `?agent=<your-agent-name>` if needed).

- [ ] **Step 3: Verify send path**

1. Send a message.
2. After the assistant replies, open DevTools → Application → Local Storage.
3. Confirm `kokibot_conv_<agentName>` is present with a UUID value.

- [ ] **Step 4: Verify restore on reload**

1. Refresh the page.
2. The chat container should show "Loading conversation…" briefly, then render all previous messages.
3. Verify user messages render as plain text and assistant messages render with markdown.

- [ ] **Step 5: Verify continuation**

1. Send another message after the page reload.
2. Open DevTools → Network → WS → the outgoing frame should include `"conversationId":"<same-uuid>"`.
3. The server should NOT create a new conversation entry (same ID echoed back in the FINAL response).

- [ ] **Step 6: Verify New Chat**

1. Click "New Chat" in the sidebar.
2. The chat container clears.
3. `kokibot_conv_<agentName>` is removed from localStorage (confirm in DevTools).
4. Send a message — a new UUID appears in localStorage after the response.

- [ ] **Step 7: Verify error resilience**

1. Open DevTools → Application → Local Storage.
2. Manually set `kokibot_conv_<agentName>` to `"nonexistent-id"`.
3. Refresh the page.
4. The chat starts empty (no error shown to the user, bad ID is silently cleared from localStorage).

- [ ] **Step 8: Commit any CSS fixes if needed, then done**

If the "Loading conversation…" placeholder needs styling, add to `chat.css`:

```css
.history-loading {
    text-align: center;
    color: var(--color-text-secondary);
    padding: 16px;
    font-size: 0.9em;
}
```

```bash
git add src/main/resources/static/css/chat.css
git commit -m "feat: add history-loading placeholder style"
```
