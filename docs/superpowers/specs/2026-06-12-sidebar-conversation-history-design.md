# Sidebar Conversation History — Design Spec

**Date:** 2026-06-12
**Branch:** feat/chat-history-conversations

## Goal

Show the past 30 conversations in the sidebar, grouped by date, loading asynchronously so page load is not impacted. The list is always visible (no toggle required) and scrollable when there are many conversations.

---

## Layout & Structure

The sidebar content area becomes two stacked zones:

```
sidebar
├── nav zone (fixed, not scrollable)
│   ├── New Chat button
│   ├── History button (disabled)
│   └── Settings button
├── <hr> divider
└── conversation list zone (flex: 1, overflow-y: auto)
    ├── "Loading…" placeholder (while fetching)
    ├── Group: Today
    │   ├── conversation item (active highlighted)
    │   └── conversation item
    ├── Group: Yesterday
    │   └── conversation item
    ├── Group: Previous 7 days
    │   └── …
    └── Group: Older
        └── …
```

When the sidebar is collapsed (icon-only mode), the conversation list zone is hidden — only nav icons remain visible.

Each conversation item shows the title on a single line, truncated with ellipsis. No click action in this iteration.

The active conversation (matching `ChatUI.conversationId`) is highlighted with a distinct background.

---

## Data & Loading

### Backend changes

**`ConversationController`** — `userId` is removed from both endpoints. The controller hardcodes `userId = "anonymous"` internally (the WebSocket channel always uses this value). The list endpoint gains `limit` (default 30) and `offset` (default 0) query parameters.

```
GET /assistants/{name}/conversations?limit=30&offset=0
GET /assistants/{name}/conversations/{id}
```

**`ConversationRepository.getConversations()`** gains `limit: Int = 30` and `offset: Int = 0` parameters, applied after sorting by `startDate` descending:
```kotlin
.sortedByDescending { it.startDate }
.drop(offset)
.take(limit)
```

### Frontend loading

A new `ConversationHistory` component fires `GET /assistants/{agentName}/conversations?limit=30&offset=0` asynchronously on `init()`, independently of the WebSocket connection and `loadConversationHistory`. The nav buttons render immediately; conversations load in below.

**Loading states:**
- While fetching: show "Loading…" placeholder in the list zone
- On success: group and render conversations
- On failure: show nothing silently (non-critical UI — no error shown)

### Date grouping (client-side)

Conversations are grouped by `startDate` relative to today:

| Group label | Condition |
|---|---|
| Today | `startDate` is today |
| Yesterday | `startDate` is yesterday |
| Previous 7 days | `startDate` is 2–7 days ago |
| Older | `startDate` is more than 7 days ago |

Groups with no conversations are omitted.

### Active conversation highlight

`ChatUI` calls `ConversationHistory.setActiveConversation(id)` whenever `this.conversationId` changes (after `handleFinalResponse` saves a new id). The component scans its rendered items and toggles an `active` class.

### Refresh on new conversation

When `setActiveConversation(id)` is called with an id not currently in the list, the component re-fetches the list so the new conversation appears at the top.

---

## Files

### Backend
| File | Change |
|---|---|
| `ConversationController.kt` | Remove `userId` param; add `limit`/`offset` to list; hardcode `"anonymous"` |
| `ConversationRepository.kt` | Add `limit`/`offset` to `getConversations()` |
| `ConversationControllerTest.kt` | Remove `?userId=...`; add limit/offset tests |
| `ConversationRepositoryTest.kt` | Add limit/offset tests |

### Frontend
| File | Change |
|---|---|
| `index.html` | Add `<div id="conversation-history">` in sidebar below nav divider |
| `css/chat.css` | Sidebar flex layout for two zones; group label styles; item styles; active highlight; hide list when collapsed |
| `js/components/conversation-history.js` | New component: fetch, group, render, `setActiveConversation(id)`, refresh |
| `js/components/sidebar.js` | Call `ConversationHistory.init(agentName)` |
| `js/chat-ui.js` | Remove `?userId=anonymous` from `loadConversationHistory` fetch; call `ConversationHistory.setActiveConversation(id)` in `handleFinalResponse` |

---

## Out of Scope

- Clicking a conversation to load it (future iteration)
- Pagination beyond the first 30
- Per-channel filtering in the sidebar
- Deleting or renaming conversations
