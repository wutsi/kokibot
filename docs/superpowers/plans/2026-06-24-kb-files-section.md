# KB Files Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Files section to the Knowledge Base settings tab so users can view ingested files and upload new ones.

**Architecture:** Frontend-only change to `settings.js`. The `renderKnowledgeBase()` method is extended to append a Files section below the existing toggles, using the already-available `GET /entries` and `POST /upload` backend endpoints.

**Tech Stack:** Vanilla JS, Spring Boot static resources, existing `Notifications` and `MarkdownRenderer` globals.

## Global Constraints

- No changes to `settings.html`, backend controllers, or any Kotlin file
- Match existing JS style: async/await, AbortController timeouts, `Notifications.*` for feedback
- Escape all user-supplied strings with `this.escapeHtml()` before inserting into innerHTML
- 10-second fetch timeout on all API calls (matches rest of settings.js)

---

### Task 1: Extend `renderKnowledgeBase()` with Files section HTML and wire upload

**Files:**
- Modify: `src/main/resources/static/js/settings.js`

**Interfaces:**
- Produces: `#kb-files-list` div (target for Task 2 rendering), `#kb-upload-btn` button, `#kb-file-input` hidden file input

- [ ] **Step 1: Open the file and locate `renderKnowledgeBase()`**

  The method is at line ~1559 in `settings.js`. It currently sets `contentElement.innerHTML` to a string containing the two toggles, then calls `this.setupKBListeners()`.

- [ ] **Step 2: Replace the method body to append the Files section**

  Find this block (the entire innerHTML assignment in `renderKnowledgeBase`):

  ```js
  contentElement.innerHTML = `
      <div class="heartbeat-settings">
          <div class="memory-setting-row">
              ...Enable Knowledge Base toggle...
          </div>
          <div class="memory-setting-row memory-setting-row-last">
              ...Exclusive Mode toggle...
          </div>
      </div>
  `;
  ```

  Replace with (keep the toggles exactly as they are, append the Files section after `</div>` that closes `heartbeat-settings`):

  ```js
  contentElement.innerHTML = `
      <div class="heartbeat-settings">
          <div class="memory-setting-row">
              <div class="memory-setting-label-group">
                  <span class="memory-setting-name">Enable Knowledge Base</span>
                  <span class="memory-setting-hint">Use the knowledge base to answer queries</span>
              </div>
              <label class="memory-toggle" title="Toggle knowledge base">
                  <input type="checkbox" id="kb-enabled-toggle"${enabled ? ' checked' : ''}>
                  <span class="memory-toggle-slider"></span>
              </label>
          </div>
          <div class="memory-setting-row memory-setting-row-last">
              <div class="memory-setting-label-group">
                  <span class="memory-setting-name">Exclusive Mode</span>
                  <span class="memory-setting-hint">Search only the knowledge base, not the LLM</span>
              </div>
              <label class="memory-toggle" title="Toggle exclusive mode">
                  <input type="checkbox" id="kb-exclusive-toggle"${exclusive ? ' checked' : ''}>
                  <span class="memory-toggle-slider"></span>
              </label>
          </div>
      </div>
      <div class="heartbeat-instructions-section">
          <div class="settings-section-header heartbeat-instructions-header">
              <div>
                  <h3 class="general-section-title">Files</h3>
                  <span class="memory-setting-hint">Documents ingested into the knowledge base</span>
              </div>
              <div class="settings-section-actions">
                  <button class="settings-action-btn settings-action-btn-primary" id="kb-upload-btn">
                      <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                          <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
                      </svg>
                      Upload
                  </button>
                  <input type="file" id="kb-file-input" style="display:none;">
              </div>
          </div>
          <div id="kb-files-list"></div>
      </div>
  `;
  ```

- [ ] **Step 3: Wire upload button and file input after the innerHTML assignment**

  At the end of `renderKnowledgeBase()`, after `this.setupKBListeners();`, add:

  ```js
  document.getElementById('kb-upload-btn')?.addEventListener('click', () => {
      document.getElementById('kb-file-input')?.click();
  });
  document.getElementById('kb-file-input')?.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) this.uploadKBFile(file);
      e.target.value = '';
  });
  this.loadKBFiles();
  ```

- [ ] **Step 4: Build the project to check for syntax errors**

  ```bash
  mvn antrun:run@ktlint-format && mvn test -pl . -Dtest=KnowledgeBaseTest
  ```

  Expected: BUILD SUCCESS (JS is not compiled by Maven but the build will still catch any Kotlin regressions from the staged files).

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/resources/static/js/settings.js
  git commit -m "feat: add Files section scaffold to KB settings tab"
  ```

---

### Task 2: Add `loadKBFiles()` and `renderKBFiles()`

**Files:**
- Modify: `src/main/resources/static/js/settings.js`

**Interfaces:**
- Consumes: `#kb-files-list` div (from Task 1)
- Produces: `loadKBFiles()` method, `renderKBFiles(entries)` method

- [ ] **Step 1: Add `loadKBFiles()` method to the `Settings` object**

  Add after the `showKBError()` method (just before `escapeHtml`):

  ```js
  async loadKBFiles() {
      const listEl = document.getElementById('kb-files-list');
      if (!listEl) return;

      listEl.innerHTML = `
          <div class="kb-loading">
              <svg class="loading-spinner" fill="currentColor" height="32" viewBox="0 0 24 24" width="32">
                  <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
              </svg>
          </div>
      `;

      try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 10000);
          const response = await fetch(`/assistants/${this.agentName}/knowledge-base/entries`, {
              signal: controller.signal,
          });
          clearTimeout(timeoutId);

          if (!response.ok) throw new Error(`HTTP ${response.status}`);

          const entries = await response.json();
          this.renderKBFiles(entries);
      } catch (error) {
          console.error('Error loading KB files:', error);
          listEl.innerHTML = `
              <div class="skills-error">
                  <p>${error.name === 'AbortError' ? 'Request timed out.' : 'Failed to load files.'}</p>
              </div>
          `;
      }
  },
  ```

- [ ] **Step 2: Add `renderKBFiles()` method**

  Add immediately after `loadKBFiles()`:

  ```js
  renderKBFiles(entries) {
      const listEl = document.getElementById('kb-files-list');
      if (!listEl) return;

      if (!entries || entries.length === 0) {
          listEl.innerHTML = `
              <div class="skills-empty">
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"/>
                  </svg>
                  <h3>No Files</h3>
                  <p>No files have been ingested yet</p>
              </div>
          `;
          return;
      }

      const rowsHtml = entries.map(entry => {
          const keywordsHtml = (entry.keywords || [])
              .map(k => `<span class="marketplace-skill-tag">${this.escapeHtml(k)}</span>`)
              .join('');
          return `
              <div class="channel-item" style="flex-direction:column;align-items:flex-start;gap:4px;">
                  <span class="channel-name">${this.escapeHtml(entry.filename)}</span>
                  ${entry.scope ? `<span class="channel-source" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%;">${this.escapeHtml(entry.scope)}</span>` : ''}
                  ${keywordsHtml ? `<div class="marketplace-skills" style="margin-top:4px;">${keywordsHtml}</div>` : ''}
              </div>
          `;
      }).join('');

      listEl.innerHTML = `<div class="channels-list">${rowsHtml}</div>`;
  },
  ```

- [ ] **Step 3: Manually verify loading state and rendering**

  Start the app: `mvn spring-boot:run`

  Open the settings page for an agent. Navigate to the Knowledge Base tab.
  - Expected: toggles visible, Files section header visible, spinner appears briefly, then either file rows or empty state renders.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/resources/static/js/settings.js
  git commit -m "feat: load and render KB file entries in settings"
  ```

---

### Task 3: Add `uploadKBFile()`

**Files:**
- Modify: `src/main/resources/static/js/settings.js`

**Interfaces:**
- Consumes: `#kb-upload-btn` (from Task 1), `loadKBFiles()` (from Task 2)
- Produces: `uploadKBFile(file)` method

- [ ] **Step 1: Add `uploadKBFile()` method**

  Add immediately after `renderKBFiles()`:

  ```js
  async uploadKBFile(file) {
      const btn = document.getElementById('kb-upload-btn');
      if (btn) {
          btn.disabled = true;
          btn.innerHTML = `
              <svg class="loading-spinner" fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                  <path d="M12,4V2A10,10 0 0,0 2,12H4A8,8 0 0,1 12,4Z"/>
              </svg>
              Uploading…
          `;
      }

      const formData = new FormData();
      formData.append('file', file, file.name);

      try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 60000);
          const response = await fetch(`/assistants/${this.agentName}/knowledge-base/upload`, {
              method: 'POST',
              body: formData,
              signal: controller.signal,
          });
          clearTimeout(timeoutId);

          if (response.status === 409) {
              Notifications.error('File already ingested', { duration: 5000 });
              return;
          }
          if (!response.ok) throw new Error(`HTTP ${response.status}`);

          Notifications.success(`${file.name} ingested successfully`, { duration: 3000 });
          await this.loadKBFiles();
      } catch (error) {
          console.error('Error uploading KB file:', error);
          Notifications.error(
              error.name === 'AbortError' ? 'Upload timed out. Please try again.' : 'Failed to upload file. Please try again.',
              { duration: 5000 }
          );
      } finally {
          if (btn) {
              btn.disabled = false;
              btn.innerHTML = `
                  <svg fill="currentColor" height="16" viewBox="0 0 24 24" width="16">
                      <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
                  </svg>
                  Upload
              `;
          }
      }
  },
  ```

- [ ] **Step 2: Manually verify upload flow**

  With the app running:
  1. Navigate to Knowledge Base settings tab
  2. Click Upload, pick any PDF or text file
  3. Expected: button disables + shows spinner, then success notification, file list reloads showing new entry
  4. Upload the same file again
  5. Expected: error notification "File already ingested"

- [ ] **Step 3: Run the full build**

  ```bash
  mvn antrun:run@ktlint-format && mvn clean install
  ```

  Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/resources/static/js/settings.js
  git commit -m "feat: upload files to knowledge base from settings UI"
  ```
