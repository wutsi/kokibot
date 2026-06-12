# Sidebar Conversation History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the past 30 conversations in the sidebar, grouped by date, loaded asynchronously, with a scrollable panel.

**Architecture:** Backend gains `limit`/`offset` on the list endpoint. A new `ConversationHistory` JS component fetches the list on page load (independent of the WebSocket), groups items by date, renders them into a scrollable zone at the bottom of the sidebar, and highlights the active conversation. `ChatUI` notifies `ConversationHistory` whenever `conversationId` changes.

**Tech Stack:** Kotlin, Spring Boot 4, JUnit 5, Mockito Kotlin, vanilla JS, CSS.

---

## File Map

### Backend
| File | Change |
|---|---|
| `src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt` | Add `limit`/`offset` to `getConversations()` |
| `src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt` | Add `limit`/`offset` to `list()`; pass `Int.MAX_VALUE` in `get()` |
| `src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt` | Add limit/offset tests |
| `src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt` | Update mock stubs; add limit/offset test |

### Frontend
| File | Change |
|---|---|
| `src/main/resources/static/js/components/conversation-history.js` | New component: fetch, group, render, `setActiveConversation` |
| `src/main/resources/static/index.html` | Add divider + `#conversation-history` zone; add script tag |
| `src/main/resources/static/css/chat.css` | Flex sidebar layout; group/item/active styles |
| `src/main/resources/static/js/components/sidebar.js` | Call `ConversationHistory.init()` |
| `src/main/resources/static/js/chat-ui.js` | Call `ConversationHistory.setActiveConversation()` in `handleFinalResponse` |

---

## Task 1: Add `limit`/`offset` to `ConversationRepository`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt:49-55`
- Modify: `src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt`

- [ ] **Step 1: Add the two new tests**

Append these tests to `ConversationRepositoryTest`:

```kotlin
@Test
fun `getConversations respects limit`() {
    repeat(5) { i -> repo.createConversation("user-1", "telegram", "Conv $i") }

    val result = repo.getConversations("user-1", limit = 3)

    assertEquals(3, result.size)
}

@Test
fun `getConversations respects offset`() {
    repeat(5) { i ->
        Thread.sleep(5)
        repo.createConversation("user-1", "telegram", "Conv $i")
    }
    val all = repo.getConversations("user-1")
    val paginated = repo.getConversations("user-1", offset = 2)

    assertEquals(all.size - 2, paginated.size)
    assertEquals(all[2].id, paginated[0].id)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ConversationRepositoryTest#"getConversations respects limit" -q 2>&1 | tail -5
```
Expected: compilation error (`getConversations` has no `limit` parameter yet).

- [ ] **Step 3: Update `getConversations()` signature and body**

Replace the existing `getConversations` function in `ConversationRepository.kt`:

```kotlin
fun getConversations(userId: String, channelId: String? = null, limit: Int = Int.MAX_VALUE, offset: Int = 0): List<Conversation> {
    lock.read {
        val sanitized = channelId?.let { sanitizeId(it) }
        return readIndex(userId)
            .filter { sanitized == null || it.channelId == sanitized }
            .sortedByDescending { it.startDate }
            .drop(offset)
            .take(limit)
    }
}
```

Note: the default `limit = Int.MAX_VALUE` means no limit by default, so existing callers that omit `limit` are unaffected. The controller explicitly passes `30`.

- [ ] **Step 4: Run the repository tests**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=ConversationRepositoryTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt \
        src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt
git commit -m "feat: add limit/offset to ConversationRepository.getConversations()"
```

---

## Task 2: Add `limit`/`offset` to `ConversationController`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt`

- [ ] **Step 1: Update the `list` test and add a limit/offset test**

In `ConversationControllerTest.kt`, the `list returns conversations for user` test uses a mock stub with 2 args. After the change, `getConversations` has 4 args — update the stub and add a new test.

Replace the existing `list returns conversations for user` test body and add a new test:

```kotlin
@Test
fun `list returns conversations for user`() {
    val conversations = listOf(
        Conversation(
            id = "conv-001",
            channelId = "telegram",
            title = "Weather in Paris",
            startDate = LocalDateTime.of(2026, 6, 12, 10, 0),
        ),
    )
    doReturn(conversations)
        .whenever(conversationRepository)
        .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())

    val response = rest.getForEntity(
        "/assistants/my-agent/conversations",
        List::class.java,
    )

    assertEquals(200, response.statusCode.value())
    assertEquals(1, response.body!!.size)
}

@Test
fun `list passes limit and offset to repository`() {
    doReturn(emptyList<Conversation>())
        .whenever(conversationRepository)
        .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), eq(5), eq(10))

    val response = rest.getForEntity(
        "/assistants/my-agent/conversations?limit=5&offset=10",
        List::class.java,
    )

    assertEquals(200, response.statusCode.value())
}
```

Also update the `get returns conversation detail` test stub (it calls `getConversations` with 4 args too, because the `get` handler will pass `limit = Int.MAX_VALUE`):

```kotlin
doReturn(listOf(conversation)).whenever(conversationRepository)
    .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())
```

And the `get returns 404 when conversation not found` stub:

```kotlin
doReturn(emptyList<Conversation>()).whenever(conversationRepository)
    .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())
```

Add `import com.nhaarman.mockitokotlin2.any` to the imports block at the top of `ConversationControllerTest.kt`.

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ConversationControllerTest -q 2>&1 | tail -5
```
Expected: compilation error or mock mismatch.

- [ ] **Step 3: Update `ConversationController`**

Replace the full content of `ConversationController.kt`:

```kotlin
package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.channel.websocket.WebSocketChannel
import com.wutsi.kokibot.service.memory.ConversationDetail
import com.wutsi.kokibot.service.memory.ConversationRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assistants")
class ConversationController(private val multi: MultiBootstrap) {

    @GetMapping("/{name}/conversations")
    fun list(
        @PathVariable name: String,
        @RequestParam(required = false) channelId: String?,
        @RequestParam(defaultValue = "30") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<Any> {
        val userId = WebSocketChannel.ANONYMOUS_USER
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(repository.getConversations(userId, channelId, limit, offset))
    }

    @GetMapping("/{name}/conversations/{id}")
    fun get(
        @PathVariable name: String,
        @PathVariable id: String,
    ): ResponseEntity<Any> {
        val userId = WebSocketChannel.ANONYMOUS_USER
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        val conversation = repository.getConversations(userId, limit = Int.MAX_VALUE).find { it.id == id }
            ?: return ResponseEntity.notFound().build()
        val messages = repository.getMessages(id, userId)
        return ResponseEntity.ok(
            ConversationDetail(
                id = conversation.id,
                title = conversation.title,
                startDate = conversation.startDate,
                messages = messages,
            )
        )
    }

    private fun getRepository(name: String): ConversationRepository? =
        multi.bootstraps
            .firstOrNull { it.getContext().assistant.name == name }
            ?.getContext()
            ?.conversationRepository
}
```

- [ ] **Step 4: Run the controller tests**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=ConversationControllerTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Run full test suite**

```bash
mvn test -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt \
        src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt
git commit -m "feat: add limit/offset query params to ConversationController list endpoint"
```

---

## Task 3: Create `ConversationHistory` JS component

**Files:**
- Create: `src/main/resources/static/js/components/conversation-history.js`

This component has no automated test (it's pure DOM rendering over a real API). Verification is visual in Task 6.

- [ ] **Step 1: Create the file**

Create `src/main/resources/static/js/components/conversation-history.js` with this content:

```javascript
/**
 * ConversationHistory Component
 * Fetches and renders past conversations in the sidebar, grouped by date.
 * Loads asynchronously on init — does not block page load.
 */
const ConversationHistory = {
    agentName: null,
    conversations: [],
    listEl: null,

    init(agentName) {
        this.agentName = agentName;
        this.listEl = document.getElementById('conversation-history');
        if (!this.listEl) return;
        this._load();
    },

    setActiveConversation(id) {
        if (!id || !this.listEl) return;
        const inList = this.conversations.some(c => c.id === id);
        if (!inList) {
            this._load().then(() => this._applyActive(id));
        } else {
            this._applyActive(id);
        }
    },

    async _load() {
        if (!this.listEl) return;
        this.listEl.innerHTML = '<div class="conv-loading">Loading…</div>';
        try {
            const res = await fetch(`/assistants/${this.agentName}/conversations?limit=30&offset=0`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            this.conversations = await res.json();
            this._render();
        } catch (e) {
            console.warn('Failed to load conversation list:', e);
            this.listEl.innerHTML = '';
        }
    },

    _render() {
        if (!this.listEl) return;
        const groups = this._groupByDate(this.conversations);
        let html = '';
        for (const [label, items] of groups) {
            if (!items.length) continue;
            html += `<div class="conv-group-label">${label}</div>`;
            for (const conv of items) {
                const safe = this._esc(conv.title);
                html += `<button class="conv-item" data-id="${conv.id}" title="${safe}">${safe}</button>`;
            }
        }
        this.listEl.innerHTML = html;
    },

    _applyActive(id) {
        if (!this.listEl) return;
        this.listEl.querySelectorAll('.conv-item').forEach(el => {
            el.classList.toggle('active', el.dataset.id === id);
        });
    },

    _groupByDate(convs) {
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const yesterday = new Date(today);
        yesterday.setDate(today.getDate() - 1);
        const weekAgo = new Date(today);
        weekAgo.setDate(today.getDate() - 7);

        const todayGroup = [];
        const yesterdayGroup = [];
        const weekGroup = [];
        const olderGroup = [];

        for (const conv of convs) {
            const d = new Date(conv.startDate);
            const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
            if (day >= today) todayGroup.push(conv);
            else if (day >= yesterday) yesterdayGroup.push(conv);
            else if (day >= weekAgo) weekGroup.push(conv);
            else olderGroup.push(conv);
        }

        return [
            ['Today', todayGroup],
            ['Yesterday', yesterdayGroup],
            ['Previous 7 days', weekGroup],
            ['Older', olderGroup],
        ];
    },

    _esc(str) {
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    },
};
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/js/components/conversation-history.js
git commit -m "feat: add ConversationHistory sidebar component"
```

---

## Task 4: Update `index.html` sidebar structure

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add divider + conversation list zone inside sidebar**

In `index.html`, inside `<div class="sidebar-content">`, add the following immediately after the closing `</nav>` tag:

```html
        <hr class="sidebar-divider">
        <div id="conversation-history" class="conv-history-list"></div>
```

The sidebar content block should now look like:
```html
    <div class="sidebar-content">
        <nav class="sidebar-nav">
            <button id="new-chat-btn" ...>...</button>
            <button id="history-btn" ...>...</button>
            <button id="settings-btn" ...>...</button>
        </nav>
        <hr class="sidebar-divider">
        <div id="conversation-history" class="conv-history-list"></div>
    </div>
```

- [ ] **Step 2: Add `conversation-history.js` script tag**

In `index.html`, add `<script src="js/components/conversation-history.js"></script>` immediately before the existing `<script src="js/components/sidebar.js"></script>` line.

The script block should look like:
```html
<script src="js/components/conversation-history.js"></script>
<script src="js/components/sidebar.js"></script>
<script src="js/components/file-upload.js"></script>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: add conversation history zone to sidebar HTML"
```

---

## Task 5: Add CSS for the conversation history panel

**Files:**
- Modify: `src/main/resources/static/css/chat.css`

- [ ] **Step 1: Make `.sidebar-content` a flex column**

Find the existing `.sidebar-content` rule (around line 215) and replace it with:

```css
.sidebar-content {
    padding: 56px 12px 0 12px;
    overflow: hidden;
    height: 100%;
    opacity: 1;
    transition: opacity 0.2s ease;
    display: flex;
    flex-direction: column;
}
```

Key changes: `overflow-y: auto` → `overflow: hidden`; `padding-bottom: 24px` → `0`; add `display: flex; flex-direction: column`.

- [ ] **Step 2: Ensure `.sidebar-nav` doesn't grow**

Find the existing `.sidebar-nav` rule and add `flex-shrink: 0`:

```css
.sidebar-nav {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex-shrink: 0;
}
```

- [ ] **Step 3: Add all new conversation history styles**

Append the following block to the end of `chat.css` (before any `@media` queries at the end of the file, or after the last existing rule):

```css
/* ===== Sidebar Divider ===== */
.sidebar-divider {
    border: none;
    border-top: 1px solid var(--color-border-light);
    margin: 8px 0;
    flex-shrink: 0;
}

/* ===== Conversation History Panel ===== */
.conv-history-list {
    flex: 1;
    overflow-y: auto;
    min-height: 0;
    padding-bottom: 16px;
}

.conv-history-list::-webkit-scrollbar {
    width: 4px;
}

.conv-history-list::-webkit-scrollbar-track {
    background: transparent;
}

.conv-history-list::-webkit-scrollbar-thumb {
    background: var(--color-border-medium);
    border-radius: 2px;
}

.conv-group-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--color-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 12px 16px 4px 16px;
}

.conv-item {
    display: block;
    width: 100%;
    padding: 7px 16px;
    background: transparent;
    border: none;
    border-radius: 6px;
    color: var(--color-text-primary);
    font-size: 13px;
    font-family: inherit;
    cursor: pointer;
    text-align: left;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: background-color 0.2s;
}

.conv-item:hover {
    background-color: var(--color-bg-tertiary);
}

.conv-item.active {
    background-color: var(--color-bg-user-message);
    color: var(--color-accent-blue);
    font-weight: 500;
}

.conv-loading {
    font-size: 13px;
    color: var(--color-text-tertiary);
    padding: 12px 16px;
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/chat.css
git commit -m "feat: add sidebar flex layout and conversation history panel styles"
```

---

## Task 6: Wire `sidebar.js` and `chat-ui.js`

**Files:**
- Modify: `src/main/resources/static/js/components/sidebar.js`
- Modify: `src/main/resources/static/js/chat-ui.js`

- [ ] **Step 1: Update `sidebar.js` to init ConversationHistory**

In `sidebar.js`, update the `init()` method to call `ConversationHistory.init()`. The `getAgentNameFromURL()` utility is already used in `handleSettings()` in the same file.

Replace the existing `init()` method:

```javascript
init() {
    this.setupElements();
    this.loadState();
    this.setupEventListeners();
    ConversationHistory.init(getAgentNameFromURL());
},
```

Also remove the `disabled` attribute workaround from `handleHistory` if desired — the History button can remain as-is for now.

- [ ] **Step 2: Update `chat-ui.js` to notify ConversationHistory**

In `chat-ui.js`, find `handleFinalResponse` and add `ConversationHistory.setActiveConversation(conversationId)` after saving the conversation ID to localStorage:

```javascript
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
},
```

- [ ] **Step 3: Run the backend test suite to confirm no regressions**

```bash
mvn clean install -q
```
Expected: BUILD SUCCESS, jacoco coverage thresholds pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/components/sidebar.js \
        src/main/resources/static/js/chat-ui.js
git commit -m "feat: wire ConversationHistory into sidebar and chat-ui"
```

---

## Final Verification

- [ ] **Start the app and open the UI**

```bash
mvn spring-boot:run
```
Open `http://localhost:8080/?agent=<your-agent-name>`.

- [ ] **Visual checks**
  - [ ] Sidebar shows a "Loading…" placeholder below the nav buttons, then the conversation list appears
  - [ ] Conversations are grouped under date labels (Today / Yesterday / Previous 7 days / Older)
  - [ ] The active conversation (matching the current chat) is highlighted in blue
  - [ ] The list is scrollable when there are many conversations
  - [ ] When the sidebar is collapsed, the conversation list is hidden
  - [ ] Sending a new message creates a new conversation entry at the top of Today
  - [ ] `GET /assistants/{name}/conversations?limit=5&offset=0` returns 5 items
  - [ ] `GET /assistants/{name}/conversations?limit=5&offset=5` returns the next 5
