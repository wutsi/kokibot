# Sidebar Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat sidebar and modal agent switcher with a structured four-section sidebar: current agent, all agents list, scrollable chat history, and status footer.

**Architecture:** Pure frontend change — HTML restructure in `index.html`, CSS additions in `chat.css`, and `sidebar.js` extended to own agent loading and rendering. The `agent-selector.js` modal component and all its HTML/CSS are deleted entirely.

**Tech Stack:** Vanilla JS (ES6), HTML5, CSS3 — no build step, served as Spring Boot static resources.

## Global Constraints

- No external libraries or new dependencies
- Follow existing patterns: vanilla JS objects (`const Foo = { init() {} }`), same CSS variable names, same `onerror`/`onclick` patterns used elsewhere
- Run `mvn antrun:run@ktlint-format` before any commit (even for frontend-only changes, the hook runs on all commits)
- Verify with `mvn compile` — static files are not compiled but the step confirms nothing is broken in the Java side

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/resources/static/index.html` | Modify | Remove modal block + agent-selector-btn; restructure sidebar into 4 sections |
| `src/main/resources/static/css/chat.css` | Modify | Remove modal/agent-selector CSS; add Section 1 + 2 styles + icon styles |
| `src/main/resources/static/js/components/sidebar.js` | Modify | Add `createAgentIcon()`, Section 1 rendering, Section 2 agent loading + rendering |
| `src/main/resources/static/js/components/agent-selector.js` | **Delete** | Replaced by Section 2 in sidebar |

---

### Task 1: Remove agent-selector modal (HTML, JS, CSS)

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/css/chat.css`
- Delete: `src/main/resources/static/js/components/agent-selector.js`

**Interfaces:**
- Produces: no `AgentSelector` global, no modal HTML, no `#agent-selector-btn`

- [ ] **Step 1: Remove modal HTML block from index.html**

In `index.html`, delete the entire `<!-- Agent Selector Modal -->` block (lines 17–27):
```html
    <!-- Agent Selector Modal -->
    <div id="agent-selector-modal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <h2>Select Agent</h2>
                <button id="close-modal-btn" class="close-button">&times;</button>
            </div>
            <div id="agent-list" class="agent-list">
                <div class="agent-list-loading">Loading agents...</div>
            </div>
        </div>
    </div>
```

- [ ] **Step 2: Remove agent-selector-btn from the chat header**

In `index.html`, replace the agent-name-container block:
```html
<!-- BEFORE -->
                <div class="agent-name-container">
                    <h1 id="agent-name">Kokibot</h1>
                    <button id="agent-selector-btn" class="agent-selector-button" title="Switch Agent">
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                            <path d="M8 11L3 6h10l-5 5z"/>
                        </svg>
                    </button>
                </div>
```
```html
<!-- AFTER -->
                <div class="agent-name-container">
                    <h1 id="agent-name">Kokibot</h1>
                </div>
```

- [ ] **Step 3: Remove agent-selector.js script tag and AgentSelector.init() call**

In `index.html`, delete:
```html
<script src="js/components/agent-selector.js"></script>
```

In the DOMContentLoaded block, delete:
```js
        AgentSelector.init(agentName);
```

- [ ] **Step 4: Delete agent-selector.js**

```bash
rm src/main/resources/static/js/components/agent-selector.js
```

- [ ] **Step 5: Remove modal and agent-selector CSS from chat.css**

Delete these CSS rule blocks from `chat.css` (they appear consecutively near line 1277):
- `.agent-selector-button { ... }`
- `.agent-selector-button:hover { ... }`
- `.modal { ... }`
- `.modal.show { ... }`
- `.modal-content { ... }`
- `@keyframes slideUp { ... }`
- `.modal-header { ... }`
- `.modal-header h2 { ... }`
- `.close-button { ... }`
- `.close-button:hover { ... }`
- `.agent-list { ... }`
- `.agent-list-loading { ... }`
- `.agent-item { ... }`
- `.agent-item:hover { ... }`
- `.agent-item.current { ... }`
- `.agent-item-name { ... }`
- `.agent-item-badge { ... }`
- `.agent-list-error { ... }`

Also delete the now-unused `.agent-name-container` and `.agent-info` rules if they become empty after the button removal (check first — `agent-name` h1 still uses `.agent-info h1`).

- [ ] **Step 6: Verify build**

```bash
mvn compile -q
```
Expected: no output (success).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/
git commit -m "Remove agent-selector modal — replaced by sidebar Section 2"
```

---

### Task 2: Restructure sidebar HTML into 4 sections

**Files:**
- Modify: `src/main/resources/static/index.html`

**Interfaces:**
- Consumes: existing `sidebar`, `sidebar-content`, `sidebar-toggle`, `conv-history-list`, `header-status` IDs/classes
- Produces:
  - `#sidebar-agent-icon-container` — placeholder div where JS injects the Section 1 icon
  - `#sidebar-agent-name` — span showing current agent name
  - `#sidebar-agents-list` — div where JS renders Section 2 agent rows
  - `#conversation-history` — unchanged (Section 3)
  - `.header-status` — unchanged (Section 4)

- [ ] **Step 1: Replace sidebar-content in index.html**

Replace the entire `<div class="sidebar-content">...</div>` block with:

```html
        <div class="sidebar-content">
            <!-- Section 1: Current Agent -->
            <div class="sidebar-section sidebar-section-current">
                <div class="sidebar-current-identity">
                    <div id="sidebar-agent-icon-container" class="sidebar-agent-icon-wrap"></div>
                    <span id="sidebar-agent-name" class="sidebar-current-name">Loading…</span>
                </div>
                <nav class="sidebar-nav">
                    <button id="new-chat-btn" class="sidebar-nav-item">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
                        </svg>
                        <span>New Chat</span>
                    </button>
                    <button id="settings-btn" class="sidebar-nav-item">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/>
                        </svg>
                        <span>Settings</span>
                    </button>
                </nav>
            </div>

            <hr class="sidebar-divider">

            <!-- Section 2: All Agents -->
            <div class="sidebar-section sidebar-section-agents">
                <div class="sidebar-section-label">Agents</div>
                <div id="sidebar-agents-list" class="sidebar-agents-list"></div>
            </div>

            <hr class="sidebar-divider">

            <!-- Section 3: Chat History -->
            <div id="conversation-history" class="conv-history-list"></div>

            <hr class="sidebar-divider">

            <!-- Section 4: Status -->
            <div class="header-status">
                <div class="connection-status">
                    <span class="status-dot" id="status-indicator"></span>
                    <span id="status-text">Connecting...</span>
                </div>
                <div class="context-window-status">
                    <span id="context-window-baseline">—</span>
                    <div class="context-window-bar-track">
                        <div class="context-window-bar-fill" id="context-window-bar"></div>
                    </div>
                    <span id="context-window-text">—</span>
                </div>
            </div>
        </div>
```

- [ ] **Step 2: Verify build**

```bash
mvn compile -q
```
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "Restructure sidebar HTML into 4 sections"
```

---

### Task 3: CSS — Section 1, Section 2, and icon styles

**Files:**
- Modify: `src/main/resources/static/css/chat.css`

**Interfaces:**
- Produces: `.sidebar-section`, `.sidebar-section-current`, `.sidebar-current-identity`, `.sidebar-current-name`, `.sidebar-agent-icon`, `.sidebar-agent-icon-fallback`, `.sidebar-agent-icon-wrap`, `.sidebar-section-agents`, `.sidebar-section-label`, `.sidebar-agents-list`, `.sidebar-agent-item`, `.sidebar-agent-item.active`

- [ ] **Step 1: Add Section 1 styles after the existing `.sidebar-nav` block**

Find the `/* ===== Sidebar Navigation ===== */` comment block and add these new rules immediately before it:

```css
/* ===== Sidebar Sections ===== */
.sidebar-section {
    flex-shrink: 0;
}

.sidebar-section-current {
    padding: 4px 0 8px;
}

.sidebar-current-identity {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 4px 8px;
}

.sidebar-agent-icon-wrap {
    flex-shrink: 0;
    display: flex;
    align-items: center;
}

.sidebar-agent-icon {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    flex-shrink: 0;
}

.sidebar-agent-icon-fallback {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    color: white;
    flex-shrink: 0;
    font-size: 13px;
}

.sidebar-current-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

/* ===== Section 2: Agents List ===== */
.sidebar-section-agents {
    padding: 0 0 4px;
}

.sidebar-section-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--color-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 0 4px 6px;
}

.sidebar-agents-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.sidebar-agent-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px;
    border-radius: 8px;
    cursor: pointer;
    border: none;
    background: transparent;
    width: 100%;
    text-align: left;
    color: var(--color-text-primary);
    font-size: 14px;
    font-family: inherit;
    transition: background-color 0.15s;
}

.sidebar-agent-item:hover {
    background-color: var(--color-bg-secondary);
}

.sidebar-agent-item.active {
    background-color: var(--color-bg-user-message);
    font-weight: 500;
}

.sidebar-agent-item-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.sidebar-agents-error {
    font-size: 12px;
    color: var(--color-error-text);
    padding: 4px;
}
```

- [ ] **Step 2: Verify build**

```bash
mvn compile -q
```
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/chat.css
git commit -m "Add CSS for sidebar sections 1 and 2, agent icon styles"
```

---

### Task 4: Update sidebar.js — icon helper, Section 1, Section 2

**Files:**
- Modify: `src/main/resources/static/js/components/sidebar.js`

**Interfaces:**
- Consumes:
  - `getAgentNameFromURL()` from `utils.js` (already used in sidebar.js)
  - `ConversationHistory.init(agentName)` (already called in sidebar.js)
  - DOM ids: `#sidebar-agent-icon-container`, `#sidebar-agent-name`, `#sidebar-agents-list`
- Produces: fully rendered sidebar Sections 1 and 2 on `init()`

- [ ] **Step 1: Replace sidebar.js with the updated implementation**

```js
/**
 * Sidebar Component
 * Manages left sidebar: current agent (S1), all agents (S2),
 * conversation history (S3), and status footer (S4).
 */
const Sidebar = {
    sidebar: null,
    toggleButton: null,
    settingsButton: null,
    storageKey: 'kokibot_sidebar_collapsed',
    agentName: null,

    init() {
        this.agentName = getAgentNameFromURL();
        this.setupElements();
        this.loadState();
        this.setupEventListeners();
        this.renderCurrentAgent();
        this.loadAgents();
        ConversationHistory.init(this.agentName);
    },

    setupElements() {
        this.sidebar = document.getElementById('sidebar');
        this.toggleButton = document.getElementById('sidebar-toggle');
        this.newChatButton = document.getElementById('new-chat-btn');
        this.settingsButton = document.getElementById('settings-btn');
    },

    loadState() {
        const isCollapsed = localStorage.getItem(this.storageKey) === 'true';
        if (isCollapsed) {
            this.sidebar.classList.add('collapsed');
        }
    },

    setupEventListeners() {
        this.toggleButton.addEventListener('click', () => this.toggle());

        if (this.newChatButton) {
            this.newChatButton.addEventListener('click', () => ChatUI.newChat());
        }

        if (this.settingsButton) {
            this.settingsButton.addEventListener('click', () => {
                window.location.href = `/settings.html?agent=${this.agentName}`;
            });
        }
    },

    toggle() {
        const isCollapsed = this.sidebar.classList.toggle('collapsed');
        localStorage.setItem(this.storageKey, isCollapsed.toString());
    },

    expand() {
        this.sidebar.classList.remove('collapsed');
        localStorage.setItem(this.storageKey, 'false');
    },

    collapse() {
        this.sidebar.classList.add('collapsed');
        localStorage.setItem(this.storageKey, 'true');
    },

    // ── Section 1 ─────────────────────────────────────────────

    renderCurrentAgent() {
        const iconContainer = document.getElementById('sidebar-agent-icon-container');
        const nameEl = document.getElementById('sidebar-agent-name');
        if (iconContainer) {
            iconContainer.appendChild(this.createAgentIcon(this.agentName, 32));
        }
        if (nameEl) {
            nameEl.textContent = this.formatAgentName(this.agentName);
        }
    },

    // ── Section 2 ─────────────────────────────────────────────

    async loadAgents() {
        const listEl = document.getElementById('sidebar-agents-list');
        if (!listEl) return;

        try {
            const res = await fetch('/assistants?channel-id=websocket');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const agents = await res.json();
            this.renderAgents(agents, listEl);
        } catch (e) {
            console.warn('Failed to load agent list:', e);
            listEl.innerHTML = `<div class="sidebar-agents-error">Could not load agents</div>`;
        }
    },

    renderAgents(agents, listEl) {
        listEl.innerHTML = '';
        agents.forEach(name => {
            const btn = document.createElement('button');
            btn.className = 'sidebar-agent-item';
            if (name === this.agentName) btn.classList.add('active');

            const icon = this.createAgentIcon(name, 28);
            const label = document.createElement('span');
            label.className = 'sidebar-agent-item-name';
            label.textContent = this.formatAgentName(name);

            btn.appendChild(icon);
            btn.appendChild(label);
            btn.addEventListener('click', () => this.switchAgent(name));
            listEl.appendChild(btn);
        });
    },

    switchAgent(name) {
        if (name === this.agentName) return;
        const url = new URL(window.location);
        url.searchParams.set('agent', name);
        window.location.href = url.toString();
    },

    // ── Shared helpers ─────────────────────────────────────────

    createAgentIcon(name, sizePx) {
        const img = document.createElement('img');
        img.src = `/assistants/${name}/icon.png`;
        img.className = 'sidebar-agent-icon';
        img.style.width = sizePx + 'px';
        img.style.height = sizePx + 'px';
        img.alt = name;
        img.onerror = () => {
            const span = document.createElement('span');
            span.className = 'sidebar-agent-icon-fallback';
            span.style.width = sizePx + 'px';
            span.style.height = sizePx + 'px';
            span.style.fontSize = Math.round(sizePx * 0.4) + 'px';
            const hue = [...name].reduce((acc, c) => acc + c.charCodeAt(0), 0) % 360;
            span.style.backgroundColor = `hsl(${hue}, 55%, 45%)`;
            span.textContent = name[0].toUpperCase();
            img.replaceWith(span);
        };
        return img;
    },

    formatAgentName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }
};
```

- [ ] **Step 2: Verify build**

```bash
mvn compile -q
```
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/components/sidebar.js
git commit -m "Update sidebar.js: add agent icon helper, render Section 1 (current agent) and Section 2 (all agents)"
```

---

### Task 5: Final wiring and cleanup

**Files:**
- Verify: `src/main/resources/static/index.html` — confirm `AgentSelector.init()` is gone
- Verify: `src/main/resources/static/css/chat.css` — confirm no dead `.agent-name-container` or `.agent-selector-button` references remain that would produce lint warnings

- [ ] **Step 1: Run full test suite to confirm no regressions**

```bash
mvn test
```
Expected: `BUILD SUCCESS`, all tests pass (sidebar changes are frontend-only, no Java tests affected).

- [ ] **Step 2: Manual smoke test**

Open `http://localhost:8080/index.html?agent=<your-agent-name>` (or run `mvn spring-boot:run` first).

Verify:
1. Sidebar Section 1: agent icon (or colored initial circle) and name appear, New Chat and Settings buttons work
2. Sidebar Section 2: list of agents loads; clicking a different agent navigates to `?agent=<name>`; active agent is highlighted
3. Sidebar Section 3: conversation history loads (up to 30 entries, scrollable)
4. Sidebar Section 4: connection dot and context window bar visible at the bottom
5. No JS console errors

- [ ] **Step 3: Commit (if any cleanup was needed)**

```bash
mvn antrun:run@ktlint-format -q
git add src/main/resources/static/
git commit -m "Sidebar restructure: 4-section layout, remove agent-selector modal"
```
