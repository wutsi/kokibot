# Design: Chat UI Cancel Button

**Date:** 2026-07-30
**Status:** Approved

## Goal

Let the user cancel a query they just sent from the chat UI (`index.html`), while it's still being processed, by
reusing the existing `POST /assistants/{name}/queries/{id}/cancel` endpoint
(see `docs/superpowers/specs/2026-07-30-query-cancellation-design.md`).

## Background

The client currently has no way to learn a query's `id`. `Message.id` (`Message.kt:12`, default
`UUID.randomUUID().toString()`) is generated server-side inside `WebSocketChannel.handleMessage()`
(`WebSocketChannel.kt:107-128`) and never sent back over the socket — `WebSocketResponse` has no `id` field, and
none of the existing frame types (`REASONING_CHUNK`, `TOOL_STATUS`, `FINAL`, `ERROR`) carry one. This has to be
fixed before any cancel button can work.

Separately, `WebSocketChannel.send()` builds its `FINAL` frame without ever setting `finishReason`
(`WebSocketChannel.kt:75-79`), even though `WebSocketResponse.finishReason` exists as a field and
`websocket-client.js`/`chat-ui.js` already thread a `finishReason` parameter through `onFinalResponse` — it's just
always `null` today. Fixing this lets the client distinguish a cancelled response from a normal answer.

## Changes

### 1. Send the query id back to the client

**`WebSocketResponseType.kt`** — add `QUEUED`:

```kotlin
enum class WebSocketResponseType {
    QUEUED,
    REASONING_CHUNK,
    TOOL_STATUS,
    FINAL,
    ERROR,
}
```

**`WebSocketResponse.kt`** — add `id`:

```kotlin
data class WebSocketResponse(
    val type: WebSocketResponseType,
    val id: String? = null,
    val content: String? = null,
    val message: String? = null,
    val finishReason: String? = null,
    val usage: LLMUsage? = null,
    val conversationId: String? = null,
)
```

**`WebSocketChannel.handleMessage()`** — capture the submitted message and ack it immediately:

```kotlin
internal fun handleMessage(session: WebSocketSession, payload: String) {
    try {
        val request = jsonMapper.readValue(payload, WebSocketRequest::class.java)
        val inboxMessage = context.inbox.submit(
            Message(
                text = request.query,
                role = Role.USER,
                userId = USER_ANONYMOUS,
                channelId = id(),
                filePaths = request.filePaths,
                conversationId = request.conversationId,
            )
        )
        sendMessage(session, WebSocketResponse(type = WebSocketResponseType.QUEUED, id = inboxMessage.id))
    } catch (e: Exception) {
        ...
    }
}
```

### 2. Populate `finishReason` on the FINAL frame

**`WebSocketChannel.send()`**:

```kotlin
val response = WebSocketResponse(
    type = WebSocketResponseType.FINAL,
    content = message.text,
    finishReason = message.finishReason?.name,
    conversationId = message.conversationId,
)
```

### 3. Client — learn and track the query id

**`websocket-client.js`** — handle the new frame type in `handleMessage()`:

```javascript
case 'QUEUED':
    if (this.handlers.onQueued) {
        this.handlers.onQueued(response.id);
    }
    break;
```

Add `onQueued: null` to the `handlers` map in the constructor.

**`connection-manager.js`** — re-emit as `'queued'`:

```javascript
this.wsClient.on('Queued', (id) => {
    this.emit('queued', id);
});
```

Add `onQueued: null` to the `handlers` map.

### 4. Client — Stop button

**`chat-ui.js`**:
- Add `currentQueryId: null` to the `ChatUI` state.
- In `setupConnectionHandlers()`, add:
  ```javascript
  this.connectionManager.on('queued', (id) => {
      this.currentQueryId = id;
      this.inputController.showStopMode();
  });
  ```
- In `handleFinalResponse()`, after the existing `this.inputController.enable()` call, add `this.currentQueryId = null;` — `enable()` (see below) already reverts the button to send mode as part of its normal behavior, so no extra call is needed there.
- Add a handler for the server `ERROR` frame path (`connectionManager.on('error', ...)`) and the WebSocket `'close'` handler to also clear `this.currentQueryId = null` — both already exist and call into `inputController.disable()`; folding the reset in there prevents a stale id from surviving a dropped connection.
- Add:
  ```javascript
  async cancelCurrentQuery() {
      if (!this.currentQueryId) return;
      const id = this.currentQueryId;
      this.inputController.disableStopButton();
      try {
          await fetch(`/assistants/${this.agentName}/queries/${id}/cancel`, { method: 'POST' });
      } catch (e) {
          console.warn('Failed to cancel query:', e);
      }
  }
  ```
  Uses plain `fetch`, matching the existing style in `chat-ui.js` (`checkAgentEnabled()`, `loadConversationHistory()`) — not the `FetchWrapper` ES module, which isn't wired into this page's non-module `<script>` setup.
- In `setupInputHandlers()`, add:
  ```javascript
  this.inputController.on('stop', () => this.cancelCurrentQuery());
  ```

**`input-controller.js`**:
- `showStopMode()`: swap the button's inner SVG to a stop-square icon, add a `.stop-mode` class, set `title`/`aria-label` to "Stop", enable the button, and rebind its click handler to fire the registered `'stop'` handler instead of `handleSend()`.
- `disableStopButton()`: keep `.stop-mode` styling but set `disabled = true` (prevents double-cancel clicks while the cooperative cancellation is still in flight).
- `enable()`: extended to also remove `.stop-mode` and restore the send-icon/click-handler, so every existing call site that already calls `enable()` (on connect, after `FINAL`, etc.) reverts the button correctly with no other changes needed.
- Internally, track the two SVG markup strings (send arrow vs. stop square) as constants so `showStopMode()`/`enable()` can swap `sendButton.innerHTML` directly — no new DOM elements, no changes to `index.html`.

**`chat.css`**:
```css
#send-button.stop-mode {
    background-color: var(--color-accent-red);
}

#send-button.stop-mode:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
```
Reuses the existing `--color-accent-red` variable — no new color introduced.

### 5. Distinct rendering for a cancelled response

**`message-renderer.js`**'s `updateFinalResponse(messageElement, content)` gains an optional `finishReason` parameter; when it's `"CANCELLED"`, the bubble gets a muted/italic style (a small `.cancelled` CSS class) instead of the normal answer styling. `chat-ui.js.handleFinalResponse()` passes `finishReason` through, which it already receives from `connectionManager`'s `'finalResponse'` event.

## Files Changed

| File | Change |
|------|--------|
| `WebSocketResponseType.kt` | New `QUEUED` value |
| `WebSocketResponse.kt` | New `id` field |
| `WebSocketChannel.kt` | `handleMessage()` sends a `QUEUED` ack with the id; `send()` populates `finishReason` |
| `websocket-client.js` | Handle `QUEUED` frame type; new `onQueued` handler slot |
| `connection-manager.js` | Re-emit `QUEUED` as `'queued'`; new `onQueued` handler slot |
| `chat-ui.js` | Track `currentQueryId`; `cancelCurrentQuery()`; wire `'queued'`/`'stop'` events |
| `input-controller.js` | `showStopMode()`, `disableStopButton()`; `enable()` reverts stop mode |
| `chat.css` | `.stop-mode` styling; `.cancelled` message styling |
| `message-renderer.js` | `updateFinalResponse()` takes `finishReason`, styles cancelled responses distinctly |

## What Does Not Change

- `index.html` — no new elements; the existing `#send-button` is reused for both modes
- The REST cancel endpoint itself (`QueryCancelController`, `Inbox.cancel()`) — no changes, already implemented
- `FetchWrapper`/`errors.bundle.js` — not adopted here; out of scope for this change

## Testing

- `WebSocketChannelTest`: `handleMessage()` sends a `QUEUED` frame carrying the submitted message's id; `send()`
  includes `finishReason` in the `FINAL` frame when `message.finishReason` is set
- Manual/browser verification (no JS test harness exists in this repo): send a message, confirm the send button
  switches to a red stop icon, click it while the assistant is still responding, confirm the request to
  `/assistants/{name}/queries/{id}/cancel` fires, and confirm the chat eventually shows a distinctly-styled
  "Query cancelled." bubble and the button reverts to send mode
