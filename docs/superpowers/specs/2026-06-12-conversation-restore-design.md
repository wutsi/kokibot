# Conversation Restore — Design Spec

**Date:** 2026-06-12
**Branch:** feat/chat-history-conversations

## Goal

Store the active `conversationId` in `localStorage` after each assistant response. On page load, fetch the conversation from the REST API and render all historical messages so the user sees their previous exchange without a page reload losing context. A "New Chat" button lets the user explicitly start a fresh conversation.

---

## Data Flow

### Send path

```
ChatUI.handleSend()
  → reads conversationId from this.conversationId (null on first send)
  → ConnectionManager.sendMessage(query, filePaths, conversationId)
  → WebSocketClient.sendMessage(query, filePaths, conversationId)
  → JSON payload: { query, filePaths, conversationId }
  → WebSocketChannel (server) — skips creating a new Conversation if conversationId is non-null
```

### Receive path

```
WebSocket FINAL response: { type, content, finishReason, usage, conversationId }
  → WebSocketClient.handleMessage() extracts conversationId from FINAL
  → calls handlers.onFinalResponse(content, finishReason, conversationId)
  → ConnectionManager emits 'finalResponse' event with (content, finishReason, conversationId)
  → ChatUI.handleFinalResponse(content, finishReason, conversationId)
  → saves conversationId to localStorage under key: kokibot_conv_{agentName}
  → sets this.conversationId = conversationId
```

### localStorage key

`kokibot_conv_{agentName}` — per-agent, stored in the browser. `userId` is always `"anonymous"` (matches server hardcoding).

---

## Page Load — Restore History

Triggered in `ChatUI.init()` after the WebSocket connects.

1. Read `conversationId` from `localStorage.getItem('kokibot_conv_{agentName}')`.
2. If none, skip — start with empty chat.
3. If present:
   - Show a "Loading conversation…" placeholder in `#chat-container`.
   - Fetch `GET /assistants/{agentName}/conversations/{conversationId}?userId=anonymous`.
   - On success: remove placeholder; render each message in `messages[]` in order:
     - `role === "user"` → `messageRenderer.addUserMessage(text)`
     - `role === "assistant"` → `messageRenderer.addAssistantMessage(text)` (new method)
   - On failure (any error, including 404): silently clear the stored ID; start with empty chat.

`addAssistantMessage(text)` is a new `MessageRenderer` method that wraps the existing `createAssistantMessage(id)` + `updateFinalResponse(element, text)` pair so markdown is rendered correctly and the avatar shows "A" (not the thinking animation).

---

## New Chat Button

Added to the sidebar above the History and Settings buttons.

**Behaviour on click:**
1. `localStorage.removeItem('kokibot_conv_{agentName}')`
2. `ChatUI.conversationId = null`
3. Clear `#chat-container` DOM (`innerHTML = ''`)

No page reload. The WebSocket stays connected. The next message will create a new `Conversation` server-side (because `conversationId` in the payload will be `null`). The button is always enabled.

---

## Files Changed

| File | Change |
|---|---|
| `static/index.html` | Add "New Chat" `<button id="new-chat-btn">` to sidebar, above history/settings buttons |
| `static/js/websocket-client.js` | `sendMessage(query, filePaths, conversationId)` — include `conversationId` in payload; `handleMessage()` FINAL case — pass `conversationId` to `onFinalResponse` handler |
| `static/js/components/connection-manager.js` | `sendMessage(query, filePaths, conversationId)` — pass through; `finalResponse` event — include `conversationId` as third arg |
| `static/js/components/message-renderer.js` | Add `addAssistantMessage(text)` |
| `static/js/components/sidebar.js` | Wire `#new-chat-btn` click → call `ChatUI.newChat()` |
| `static/js/chat-ui.js` | Add `conversationId` field; `init()` — restore history on load; `handleSend()` — pass `conversationId`; `handleFinalResponse()` — save to localStorage; add `newChat()` method |

---

## Error Handling

- Fetch failure on page load: silently discard stored ID, start empty. No toast shown (the conversation not loading is a graceful degradation, not an actionable error).
- If `conversationId` is returned as empty string or null from server: do not save to localStorage.

---

## Out of Scope

- History panel listing all past conversations (sidebar "History" button remains disabled).
- Per-user auth / non-anonymous userId.
- Conversation TTL or auto-expiry.
