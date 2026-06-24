# Sidebar Restructure — Design Spec

**Date:** 2026-06-24
**Status:** Approved

## Goal

Replace the current flat sidebar (nav buttons + conversation history + status) with a structured four-section layout that exposes the current agent, all available agents, chat history, and connection status — and removes the modal-based agent switcher entirely.

---

## Sections

### Section 1 — Current Agent (fixed height)
- Shows the active agent's icon (32 px circle, fetched from `/assistants/<name>/icon.png`)
- Agent name displayed next to the icon (formatted: `my-agent` → `My Agent`)
- **New Chat** button and **Settings** button below the agent identity row
- Fallback when icon returns 404: colored circle showing the agent's first initial

### Section 2 — All Agents (fixed height, no scroll)
- Section label: `Agents`
- Fetched from `GET /assistants?channel-id=websocket`
- Each row: 28 px icon + formatted agent name
- Active agent is highlighted
- Click navigates to `?agent=<name>` (same behavior as the removed modal)
- Icon fallback: same first-initial colored circle as Section 1
- No scroll — typically 2–5 agents; pushes history down if more

### Section 3 — Chat History (flex: 1, scrollable)
- Identical to current `ConversationHistory` component behavior
- Limited to 30 conversations, grouped by date (Today / Yesterday / Previous 30 days)
- `overflow-y: auto` with custom scrollbar styling already in place

### Section 4 — Status (fixed height, pinned at bottom)
- Connection dot + status text
- Context window progress bar
- Already in sidebar; no logic changes needed

---

## Removed

- `#agent-selector-modal` HTML block in `index.html`
- `agent-selector.js` component and its `<script>` tag
- `AgentSelector.init()` call in the DOMContentLoaded handler
- `#agent-selector-btn` button in the chat header
- Modal-related CSS rules (`.modal`, `.agent-list`, `.agent-item`, etc.)

---

## Layout

```
sidebar (flex column, height: 100%, box-sizing: border-box)
├── toggle button            (position: absolute, top-right)
└── sidebar-content          (flex column, padding-top: 56px, box-sizing: border-box)
    ├── .sidebar-section-agent      Section 1 (flex-shrink: 0)
    ├── .sidebar-divider
    ├── .sidebar-section-agents     Section 2 (flex-shrink: 0)
    ├── .sidebar-divider
    ├── #conversation-history       Section 3 (flex: 1, overflow-y: auto)
    ├── .sidebar-divider
    └── .header-status              Section 4 (flex-shrink: 0)
```

---

## Files Changed

| File | Change |
|------|--------|
| `index.html` | Restructure sidebar HTML; remove modal block and agent-selector-btn; remove `agent-selector.js` script tag |
| `css/chat.css` | Add Section 1 and 2 styles; adjust existing `.header-status` and `.conv-history-list` |
| `js/components/sidebar.js` | Add agent list loading + rendering for Section 2; render Section 1 icon/name |
| `js/components/agent-selector.js` | **Delete** |

---

## Agent Icon Component (shared by Sections 1 and 2)

Helper function `createAgentIcon(name, sizePx)`:
- Creates an `<img>` pointing to `/assistants/<name>/icon.png`
- On `onerror`: replaces with a `<span>` styled as a colored circle showing `name[0].toUpperCase()`
- Color is deterministic from the agent name (hue derived from char codes) for visual consistency

---

## Error Handling

- If `/assistants` fetch fails in Section 2: show a single-line error with a Retry button (same pattern used elsewhere in the codebase)
- If conversation history fetch fails: silent empty state (current behavior, unchanged)
- Icon 404s are silent (fallback renders automatically via `onerror`)
