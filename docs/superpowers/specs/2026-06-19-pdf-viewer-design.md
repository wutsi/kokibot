# PDF Viewer — Design Spec

**Date:** 2026-06-19
**Status:** Approved

## Overview

Replace the browser-native `<iframe>` PDF renderer in the file viewer with a pdf.js canvas-based renderer that supports multi-page navigation.

## Scope

Frontend-only change. Two files are modified:

| File | Change |
|---|---|
| `src/main/resources/static/index.html` | Add two CDN `<script>` tags for pdf.js |
| `src/main/resources/static/js/components/file-viewer.js` | Replace iframe PDF branch with canvas renderer + nav bar |

No backend changes. The existing `GET /files/preview/{path}` endpoint already serves PDFs with `Content-Disposition: inline`.

## CDN

Load pdf.js from the unpkg CDN:

```html
<script src="https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.min.mjs" type="module"></script>
```

The worker is configured inline in `file-viewer.js`:

```js
pdfjsLib.GlobalWorkerOptions.workerSrc =
    'https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.worker.min.mjs';
```

## UI Structure

When a PDF opens, `.file-viewer-content` contains:

```
.file-viewer-content
  ├── .pdf-nav-bar          ← sticky nav: Prev | Page N of M | Next
  └── .pdf-canvas-wrap      ← scrollable container
        └── <canvas>        ← pdf.js renders the current page here
```

The existing toolbar (filename, download button, close button) is unchanged.

## Behaviour

- **Loading**: `pdfjsLib.getDocument(url)` fetches and parses the PDF. A "Loading…" message is shown until the first page renders.
- **Page rendering**: `renderPage(n)` calls `page.render({ canvasContext, viewport })`. The viewport scales to fit the panel width so the page fills the available horizontal space.
- **Navigation**: Prev/Next buttons call `renderPage(currentPage ± 1)` and update the "Page N of M" counter. Prev is disabled on page 1; Next is disabled on the last page.
- **Re-open**: Opening any file clears `contentEl.innerHTML = ''` before rendering — existing behaviour, unchanged.
- **Error**: If pdf.js fails to load the document, an inline error message is shown in `.file-viewer-content` (same pattern as the existing text-fetch error).

## Changes to `file-viewer.js`

Replace the `ext === 'pdf'` branch inside `renderBinary()`:

**Before:**
```js
if (ext === 'pdf') {
    const iframe = document.createElement('iframe');
    iframe.src = url;
    iframe.title = 'PDF viewer';
    contentEl.appendChild(iframe);
}
```

**After:**
```js
if (ext === 'pdf') {
    renderPdf(url);
}
```

Add a new `renderPdf(url)` function:

```js
async function renderPdf(url) {
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://unpkg.com/pdfjs-dist@4.4.168/build/pdf.worker.min.mjs';

    // Loading indicator
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

    // Build nav bar
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

    // Canvas container
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
        const viewport = page.getViewport({ scale: wrap.clientWidth / page.getViewport({ scale: 1 }).width });
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
    }

    prevBtn.addEventListener('click', () => { if (currentPage > 1) renderPage(currentPage - 1); });
    nextBtn.addEventListener('click', () => { if (currentPage < totalPages) renderPage(currentPage + 1); });

    await renderPage(1);
}
```

## CSS additions (`viewer.css`)

```css
/* PDF nav bar */
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

## Out of Scope

- Keyboard shortcuts for page navigation — post-v1
- Zoom controls — post-v1
- Text selection / search within PDF — post-v1
- Thumbnail sidebar — post-v1
