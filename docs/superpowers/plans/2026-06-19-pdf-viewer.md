# PDF Viewer with pdf.js Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the browser-native `<iframe>` PDF renderer in the file viewer with a pdf.js canvas-based renderer that supports multi-page navigation.

**Architecture:** Load pdf.js from CDN as a global UMD script. Replace the `<iframe>` branch in `renderBinary()` with an async `renderPdf()` function that builds a nav bar (Prev / Page N of M / Next) and renders each page into a `<canvas>`. CSS additions provide styling for the nav bar and canvas container.

**Tech Stack:** pdf.js 4.4.168 (unpkg CDN, UMD build), vanilla JS, CSS custom properties already defined in the app theme.

## Global Constraints

- pdf.js CDN: `https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.min.js` (UMD, no `type="module"`)
- Worker CDN: `https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.worker.min.js`
- Both CDN URLs must pin the same version (`4.4.168`)
- No npm, no bundler — plain `<script>` tags and static JS files only
- Follow existing code style in `file-viewer.js` (IIFE, no classes, `const`/`let`)
- No backend changes

---

### Task 1: Add pdf.js CDN script and PDF styles

**Files:**
- Modify: `src/main/resources/static/index.html` (add `<script>` tag before `file-viewer.js`)
- Modify: `src/main/resources/static/css/viewer.css` (append PDF-specific rules)

**Interfaces:**
- Produces: global `pdfjsLib` available to all subsequent `<script>` tags; CSS classes `.pdf-nav-bar`, `.pdf-canvas-wrap` available for Task 2

- [ ] **Step 1: Add the pdf.js CDN script to `index.html`**

In `src/main/resources/static/index.html`, add the script tag **before** the `file-viewer.js` line (currently line 162):

```html
<script src="https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.min.js"></script>
<script src="js/components/file-viewer.js"></script>
```

The file around that area should look like:

```html
<script src="js/components/context-window.js"></script>
<script src="https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.min.js"></script>
<script src="js/components/file-viewer.js"></script>
<!-- Main orchestrator -->
<script src="js/chat-ui.js"></script>
```

- [ ] **Step 2: Append PDF CSS rules to `viewer.css`**

Append the following to the end of `src/main/resources/static/css/viewer.css`:

```css
/* ===== PDF Viewer ===== */

.pdf-nav-bar {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    padding: 6px 12px;
    border-bottom: 1px solid var(--color-border-light);
    background: var(--color-bg-secondary);
    flex-shrink: 0;
    font-size: 13px;
    color: var(--color-text-secondary);
}

.pdf-nav-bar button {
    padding: 3px 10px;
    border: 1px solid var(--color-border-medium);
    border-radius: 4px;
    background: var(--color-bg-primary);
    color: var(--color-text-secondary);
    font-size: 12px;
    cursor: pointer;
}

.pdf-nav-bar button:hover:not(:disabled) {
    background-color: var(--color-bg-secondary);
    color: var(--color-text-primary);
}

.pdf-nav-bar button:disabled {
    opacity: 0.4;
    cursor: default;
}

.pdf-canvas-wrap {
    flex: 1;
    overflow: auto;
    display: flex;
    justify-content: center;
    padding: 16px;
    min-height: 0;
}

.pdf-canvas-wrap canvas {
    display: block;
    max-width: 100%;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
```

- [ ] **Step 3: Verify the CDN script loads**

Start the app:
```bash
cd /Users/htchepannou/Perso/kokibot
mvn spring-boot:run
```

Open `http://localhost:8080` in a browser. Open the browser console and type:
```js
typeof pdfjsLib
```
Expected: `"object"` (not `"undefined"`).

- [ ] **Step 4: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot
git add src/main/resources/static/index.html src/main/resources/static/css/viewer.css
git commit -m "feat: add pdf.js CDN script and PDF viewer CSS"
```

---

### Task 2: Implement canvas-based PDF renderer in `file-viewer.js`

**Files:**
- Modify: `src/main/resources/static/js/components/file-viewer.js`

**Interfaces:**
- Consumes: global `pdfjsLib` (loaded by Task 1); CSS classes `.pdf-nav-bar`, `.pdf-canvas-wrap` (added by Task 1)
- Produces: PDFs render as navigable canvas pages inside `.file-viewer-content`

- [ ] **Step 1: Replace the `<iframe>` PDF branch in `renderBinary()`**

In `src/main/resources/static/js/components/file-viewer.js`, find `renderBinary()` (currently lines 97–109):

```js
function renderBinary(url, ext) {
    if (ext === 'pdf') {
        const iframe = document.createElement('iframe');
        iframe.src = url;
        iframe.title = 'PDF viewer';
        contentEl.appendChild(iframe);
    } else {
        const img = document.createElement('img');
        img.src = url;
        img.alt = '';
        contentEl.appendChild(img);
    }
}
```

Replace it with:

```js
function renderBinary(url, ext) {
    if (ext === 'pdf') {
        renderPdf(url); // async, errors caught internally
    } else {
        const img = document.createElement('img');
        img.src = url;
        img.alt = '';
        contentEl.appendChild(img);
    }
}
```

- [ ] **Step 2: Add `renderPdf()` after `renderBinary()`**

Insert the following function immediately after the closing `}` of `renderBinary()`:

```js
async function renderPdf(url) {
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.worker.min.js';

    contentEl.innerHTML = '<div class="viewer-text">Loading…</div>';

    let pdfDoc;
    try {
        pdfDoc = await pdfjsLib.getDocument(url).promise;
    } catch (err) {
        contentEl.innerHTML =
            `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to load PDF: ${err.message}</div>`;
        return;
    }

    const totalPages = pdfDoc.numPages;
    let currentPage = 1;

    const nav = document.createElement('div');
    nav.className = 'pdf-nav-bar';

    const prevBtn = document.createElement('button');
    prevBtn.textContent = '‹ Prev';

    const pageInfo = document.createElement('span');

    const nextBtn = document.createElement('button');
    nextBtn.textContent = 'Next ›';

    nav.appendChild(prevBtn);
    nav.appendChild(pageInfo);
    nav.appendChild(nextBtn);

    const wrap = document.createElement('div');
    wrap.className = 'pdf-canvas-wrap';

    const canvas = document.createElement('canvas');
    wrap.appendChild(canvas);

    contentEl.innerHTML = '';
    contentEl.appendChild(nav);
    contentEl.appendChild(wrap);

    async function renderPage(n) {
        currentPage = n;
        pageInfo.textContent = `Page ${n} of ${totalPages}`;
        prevBtn.disabled = n <= 1;
        nextBtn.disabled = n >= totalPages;

        const page = await pdfDoc.getPage(n);
        const scale = wrap.clientWidth / page.getViewport({ scale: 1 }).width;
        const viewport = page.getViewport({ scale });
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
    }

    prevBtn.addEventListener('click', () => { if (currentPage > 1) renderPage(currentPage - 1); });
    nextBtn.addEventListener('click', () => { if (currentPage < totalPages) renderPage(currentPage + 1); });

    await renderPage(1);
}
```

- [ ] **Step 3: Verify manually in the browser**

Start the app (if not already running):
```bash
cd /Users/htchepannou/Perso/kokibot
mvn spring-boot:run
```

Open `http://localhost:8080`. In the chat, trigger a link to any PDF file (e.g., send a message that produces a `/files/…` link pointing to a `.pdf`). Click the link.

Expected:
1. The viewer panel opens on the right.
2. A "Loading…" message appears briefly.
3. The first page of the PDF renders on a canvas.
4. The nav bar shows "‹ Prev  Page 1 of N  Next ›".
5. "‹ Prev" is disabled (greyed out).
6. Clicking "Next ›" renders page 2 and enables "‹ Prev".
7. Clicking "‹ Prev" goes back to page 1.
8. On the last page, "Next ›" is disabled.

For a quick test without going through the chat, open the browser console and run:
```js
FileViewer.open('/files/preview/agent-name|path|to|sample.pdf', '/files/agent-name|path|to|sample.pdf', 'sample.pdf');
```
(Adjust the path to match an actual PDF in the agent workspace.)

- [ ] **Step 4: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot
git add src/main/resources/static/js/components/file-viewer.js
git commit -m "feat: replace iframe PDF viewer with pdf.js canvas renderer"
```
