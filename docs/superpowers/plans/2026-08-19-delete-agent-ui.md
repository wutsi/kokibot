# Delete Agent (UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Delete Agent" action to the General settings tab so a user can delete an agent from the UI, calling the existing `DELETE /assistants/{name}` endpoint.

**Architecture:** A new "Danger Zone" subsection is appended to the General tab's dynamically-rendered content (`displayGeneralInfo()` in `settings.js`), containing a single "Delete Agent" button styled with a new `settings-action-btn-danger` CSS class. Clicking it shows a plain `window.confirm()` (matching the existing `deleteKBFile()` pattern), then calls `DELETE /assistants/{name}` and redirects to `/agents.html` on success.

**Tech Stack:** Vanilla JS (no framework), served as static assets from `src/main/resources/static/`. No JS test harness exists in this repo for this code — verification is manual, via the running app.

**Spec:** No separate spec file — design was agreed in chat (bounded change: plain `window.confirm()` for consistency with existing KB-delete pattern, new "Danger Zone" subsection at the bottom of the General tab, no changes to the agent list page).

## Global Constraints

- Match existing code style exactly: same fetch-with-`AbortController`-10s-timeout pattern used throughout `settings.js` (e.g. `deleteKBFile()`, `loadGeneral()`), same `Notifications.success()`/`Notifications.error()` calls, same button disable/spinner pattern.
- Use plain `window.confirm()` — no new modal component. This mirrors `deleteKBFile()` at `src/main/resources/static/js/settings.js:2943`.
- No changes to `agents.html`, `sidebar.js`, or any other page — this task only touches the agent's own settings page (`settings.html`, `settings.js`, `settings.css`).
- After a successful delete, redirect to `/agents.html` — the current agent no longer exists, so nothing else on the settings page can render meaningfully.

---

### Task 1: "Delete Agent" button in Danger Zone

**Files:**
- Modify: `src/main/resources/static/js/settings.js` (edit `displayGeneralInfo()` around line 268-426; add new method `deleteAgent()`)
- Modify: `src/main/resources/static/css/settings.css` (add danger button variant near the existing `.settings-action-btn-*` rules, around line 119-135)
- No test file — verify manually (see Step 4 below).

**Interfaces:**
- Consumes: `DELETE /assistants/{name}` (existing endpoint, returns `200 {"success": true}` on success, `404` if the agent is gone already), `Notifications.success(message, opts)` / `Notifications.error(message, opts)` (existing global, used elsewhere in this file), `this.agentName` (existing instance property, already set elsewhere in `SettingsPage`/equivalent object this file defines).
- Produces: `deleteAgent()` method on the same object that owns `displayGeneralInfo()` — no other task consumes it, it's wired directly to the button's click listener in this same task.

- [ ] **Step 1: Add the Danger Zone markup**

In `src/main/resources/static/js/settings.js`, inside `displayGeneralInfo()`, the template literal assigned to `contentElement.innerHTML` currently ends like this (around line 384-386):

```javascript
                    ${instructionsBodyHtml}
                </div>
            </div>
        `;
```

Change it to add a new `.setting-section` for the Danger Zone right after the Instructions section's closing `</div>`, still inside the outer `.general-info` div:

```javascript
                    ${instructionsBodyHtml}
                </div>
                <div class="setting-section">
                    <div class="setting-section-row">
                        <h3 class="setting-section-title">Danger Zone</h3>
                    </div>
                    <div class="setting-section-row">
                        <div class="setting-section-label">
                            <span class="setting-section-name">Delete this agent</span>
                            <span class="setting-section-hint">Removes the agent and moves its data to a trash folder. This cannot be undone from the UI.</span>
                        </div>
                        <button class="settings-action-btn settings-action-btn-danger" id="general-delete-agent-btn">
                            <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                            </svg>
                            Delete Agent
                        </button>
                    </div>
                </div>
            </div>
        `;
```

- [ ] **Step 2: Wire the button and add `deleteAgent()`**

Still in `displayGeneralInfo()`, right after the existing listener block that ends with the icon-upload wiring (around line 417-425):

```javascript
        const iconContainer = document.getElementById('general-agent-icon-container');
        const iconInput = document.getElementById('general-icon-upload-input');
        if (iconContainer && iconInput) {
            iconContainer.addEventListener('click', () => iconInput.click());
            iconInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file) this.uploadIcon(file);
            });
        }
    },
```

Add a new listener registration right before that closing `},` of `displayGeneralInfo()`:

```javascript
        const iconContainer = document.getElementById('general-agent-icon-container');
        const iconInput = document.getElementById('general-icon-upload-input');
        if (iconContainer && iconInput) {
            iconContainer.addEventListener('click', () => iconInput.click());
            iconInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file) this.uploadIcon(file);
            });
        }

        document.getElementById('general-delete-agent-btn')?.addEventListener('click', (e) => {
            this.deleteAgent(e.currentTarget);
        });
    },
```

Then add a new `deleteAgent()` method as a sibling of `displayGeneralInfo()` (insert it directly after the `displayGeneralInfo() { ... }` method closes, before `async loadMemory() {`):

```javascript
    async deleteAgent(btn) {
        if (!this.agentName) return;
        if (!window.confirm(`Delete agent "${this.agentName}"? This cannot be undone from the UI.`)) return;

        btn.disabled = true;
        const originalHtml = btn.innerHTML;
        btn.innerHTML = `
            <svg class="loading-spinner" fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
            </svg>
        `;

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            const response = await fetch(`/assistants/${this.agentName}`, {
                method: 'DELETE',
                signal: controller.signal
            });
            clearTimeout(timeoutId);

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            Notifications.success(`Agent "${this.agentName}" deleted`, { duration: 3000 });
            window.location.href = '/agents.html';
        } catch (error) {
            console.error('Error deleting agent:', error);
            btn.disabled = false;
            btn.innerHTML = originalHtml;
            Notifications.error(
                error.name === 'AbortError' ? 'Delete request timed out.' : 'Failed to delete agent. Please try again.',
                { duration: 5000 }
            );
        }
    },
```

- [ ] **Step 3: Add the danger button style**

In `src/main/resources/static/css/settings.css`, right after the existing `.settings-action-btn-secondary:hover:not(:disabled)` rule (around line 133-135):

```css
.settings-action-btn-secondary:hover:not(:disabled) {
    background-color: var(--color-bg-secondary);
}
```

Add:

```css
.settings-action-btn-danger {
    background-color: var(--color-accent-red);
    color: white;
}

.settings-action-btn-danger:hover:not(:disabled) {
    background-color: #b91c1c;
}
```

(`var(--color-accent-red)` is already defined and used elsewhere in this stylesheet, e.g. lines 644, 726, 1140.)

- [ ] **Step 4: Manual verification**

Run: `mvn spring-boot:run` (or however the app is normally started locally), then in a browser:
1. Navigate to an existing agent's settings page, General tab.
2. Confirm the new "Danger Zone" section renders at the bottom with a red "Delete Agent" button.
3. Click it, click "Cancel" on the browser confirm dialog — confirm nothing happens (agent still exists, no network request fires).
4. Click it again, click "OK" — confirm a success notification appears and the browser redirects to `/agents.html`.
5. Confirm the deleted agent no longer appears in the agent list on `/agents.html`.
6. Confirm on disk that `~/kokibot/agents/{name}` (or `~/.kokibot/agents/{name}` in prod) no longer exists and `~/kokibot/agents/.trash/{name}-{timestamp}/` does.

Expected: all six checks pass.

- [ ] **Step 5: Format and commit**

This is plain JS/CSS/HTML with no ktlint applicability (ktlint only covers Kotlin). Run the project's usual lint if one applies to static assets (check `package.json`/`.eslintrc` at repo root — if none exists, skip).

```bash
git add src/main/resources/static/js/settings.js src/main/resources/static/css/settings.css
git commit -m "feat: add Delete Agent action to General settings tab"
```
