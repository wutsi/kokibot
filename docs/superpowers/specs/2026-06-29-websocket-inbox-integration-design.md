# Design: WebSocketChannel + Inbox Integration

**Date:** 2026-06-29  
**Status:** Approved

## Goal

Integrate `WebSocketChannel` with the `Inbox` system so it follows the same queued, WIP-limited processing model recently adopted by `TelegramChannel`, while preserving full streaming parity for WebSocket clients.

## Background

`TelegramChannel` was refactored (commit `51a5f5e`) to submit messages to `Inbox` instead of calling `assistant.process()` inline. `InboxPoller` processes queued messages and delivers responses via `channel.send()` and `channel.sendStatus()`.

`WebSocketChannel` still processes inline. It also has a session isolation bug: all connections share `userId = "anonymous"`, so a second browser tab overwrites the first session.

## Changes

### 1. Session identification — `WebSocketChannel`

Replace `ANONYMOUS_USER` with `session.id` as the userId throughout `handleMessage()`.

Move session registration from `handleMessage()` to `handleConnectionEstablished()`:

```kotlin
override fun handleConnectionEstablished(session: WebSocketSession) {
    sessions[session.id] = session
}
```

`handleConnectionClosed()` already removes by session ID — no change needed.

### 2. Message submission — `WebSocketChannel.handleMessage()`

Stop calling `context.assistant.process()` inline. Instead:

1. Parse `WebSocketRequest` as before
2. Submit to inbox:
```kotlin
context.inbox.submit(
    InboxMessage(
        channelId = id(),
        userId = session.id,
        text = request.query,
        filePaths = request.filePaths,
        conversationId = request.conversationId,
    )
)
```
3. No response sent here — `InboxPoller` handles delivery
4. On `inbox.submit()` failure, call `sendError(session, ...)`

Remove the `lastUsage` tracking and `sendFinalResponse()` call (replaced by `send()`). The private methods `sendReasoningChunk()` and `sendFinalResponse()` become unused and should be deleted.

### 3. Stream frame type fix — `WebSocketChannel.sendStatus()`

Change the response type from `TOOL_STATUS` to `REASONING_CHUNK`. All stream callbacks from `InboxPoller` carry reasoning text deltas, not tool updates.

Also forward `message.usage` into the `WebSocketResponse`:

```kotlin
WebSocketResponse(
    type = WebSocketResponseType.REASONING_CHUNK,
    content = message.text,
    usage = message.usage,
)
```

### 4. Usage in stream messages — `Message.kt`

Add `usage: LLMUsage? = null` to `Message`. The `LLMUsage` type is already used in `channel/websocket/` and `llm/`, so this adds no new package dependency.

### 5. Usage forwarding — `InboxPoller.streamCallback()`

Pass `usage = data.usage` when constructing the `Message` sent to `channel.sendStatus()`:

```kotlin
Message(
    channelId = inboxMessage.channelId,
    userId = inboxMessage.userId,
    text = data.text,
    usage = data.usage,
    role = Role.ASSISTANT,
)
```

### 6. conversationId in FINAL response

**InboxPoller.deliver()**: add `conversationId = response.conversationId` to the Message:

```kotlin
Message(
    channelId = inboxMessage.channelId,
    userId = inboxMessage.userId,
    subject = inboxMessage.subject?.let { "Re: $it" },
    text = response.text,
    conversationId = response.conversationId,
    role = Role.ASSISTANT,
)
```

**WebSocketChannel.send()**: include `conversationId` in the FINAL frame:

```kotlin
WebSocketResponse(
    type = WebSocketResponseType.FINAL,
    content = message.text,
    conversationId = message.conversationId,
)
```

## Files Changed

| File | Change |
|------|--------|
| `Message.kt` | Add `usage: LLMUsage? = null` |
| `WebSocketChannel.kt` | Session keyed by `session.id`; `handleMessage()` submits to inbox; `sendStatus()` sends REASONING_CHUNK with usage; `send()` includes conversationId |
| `InboxPoller.kt` | `streamCallback()` forwards usage; `deliver()` forwards conversationId |

## What Does Not Change

- `Inbox.kt`, `InboxMessage.kt` — no changes
- `ChannelRegistry`, `WebSocketRouter`, `WebSocketChannelRegistry` — no changes
- `TelegramChannel` — no changes
- Client WebSocket protocol — frame types and fields are preserved

## Testing

- `WebSocketChannelTest`: verify session stored on connection, not on first message; verify two concurrent sessions route independently; verify `handleMessage()` calls `inbox.submit()` not `assistant.process()`
- `InboxPollerTest`: verify `streamCallback` includes usage in Message; verify `deliver()` includes conversationId
- Existing tests for Telegram inbox integration should continue to pass
