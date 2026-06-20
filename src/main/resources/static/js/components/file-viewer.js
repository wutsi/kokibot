/**
 * File viewer panel
 * Opens supported file types inline on the RHS when clicking /files/ links in chat
 */
const FileViewer = (() => {
    const SUPPORTED = new Set(['txt', 'md', 'html', 'htm', 'pdf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'docx', 'xlsx']);
    const TOGGLE_TYPES = new Set(['md', 'html', 'htm']);
    const BINARY_TYPES = new Set(['pdf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'docx', 'xlsx']);

    let panel, chatBody, nameEl, toggleBtn, downloadBtn, contentEl;
    let currentRawText = null;
    let currentExt = null;
    let isCodeMode = false;

    function init() {
        panel = document.getElementById('file-viewer');
        chatBody = document.querySelector('.chat-body');
        nameEl = panel.querySelector('.file-viewer-name');
        toggleBtn = panel.querySelector('.file-viewer-toggle-btn');
        downloadBtn = panel.querySelector('.file-viewer-download-btn');
        contentEl = panel.querySelector('.file-viewer-content');

        panel.querySelector('.file-viewer-close-btn').addEventListener('click', close);
        toggleBtn.addEventListener('click', toggleMode);

        document.getElementById('chat-container').addEventListener('click', (e) => {
            const link = e.target.closest('a');
            if (!link) return;

            let pathname;
            try { pathname = new URL(link.href).pathname; } catch (err) {
                return;
            }

            if (!pathname.startsWith('/files/') || pathname.startsWith('/files/preview/')) return;

            const ext = getExt(link.href);
            if (!SUPPORTED.has(ext)) return;

            e.preventDefault();
            const previewUrl = link.href.replace('/files/', '/files/preview/');
            openViewer(previewUrl, link.href, link.textContent.trim()).catch(err =>
                console.error('[FileViewer] openViewer error:', err)
            );
        });
    }

    async function openViewer(previewUrl, downloadUrl, filename) {
        const ext = getExt(previewUrl);
        currentExt = ext;
        isCodeMode = false;
        currentRawText = null;

        nameEl.textContent = filename;
        nameEl.title = filename;
        downloadBtn.onclick = () => { window.location.href = downloadUrl; };
        toggleBtn.hidden = !TOGGLE_TYPES.has(ext);
        toggleBtn.textContent = 'Code';

        contentEl.innerHTML = '';

        if (BINARY_TYPES.has(ext)) {
            renderBinary(previewUrl, ext);
        } else {
            await renderText(previewUrl, ext);
        }

        panel.classList.remove('file-viewer--hidden');
        chatBody.classList.add('viewer-open');
    }

    function close() {
        panel.classList.add('file-viewer--hidden');
        chatBody.classList.remove('viewer-open');
        contentEl.innerHTML = '';
        currentRawText = null;
        currentExt = null;
    }

    function toggleMode() {
        isCodeMode = !isCodeMode;
        toggleBtn.textContent = isCodeMode ? 'Preview' : 'Code';
        if (isCodeMode) {
            renderCodeView();
        } else {
            renderPreviewView(currentExt);
        }
    }

    function renderBinary(url, ext) {
        if (ext === 'pdf') {
            renderPdf(url); // async, errors caught internally
        } else if (ext === 'docx') {
            renderDocx(url); // async, errors caught internally
        } else if (ext === 'xlsx') {
            renderXlsx(url); // async, errors caught internally
        } else {
            const img = document.createElement('img');
            img.src = url;
            img.alt = '';
            contentEl.appendChild(img);
        }
    }

    async function renderDocx(url) {
        contentEl.innerHTML = '<div class="viewer-text">Loading…</div>';

        let blob;
        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            blob = await res.blob();
        } catch (err) {
            contentEl.innerHTML =
                `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to load document: ${escapeHtml(err.message)}</div>`;
            return;
        }

        const container = document.createElement('div');
        container.className = 'viewer-docx';
        contentEl.innerHTML = '';
        contentEl.appendChild(container);

        try {
            await docx.renderAsync(blob, container, null, { inWrapper: false });
        } catch (err) {
            contentEl.innerHTML =
                `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to render document: ${escapeHtml(err.message)}</div>`;
        }
    }

    async function renderPdf(url) {
        pdfjsLib.GlobalWorkerOptions.workerSrc =
            'https://unpkg.com/pdfjs-dist@3.11.174/build/pdf.worker.min.js';

        contentEl.innerHTML = '<div class="viewer-text">Loading…</div>';

        let pdfDoc;
        try {
            pdfDoc = await pdfjsLib.getDocument(url).promise;
        } catch (err) {
            contentEl.innerHTML =
                `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to load PDF: ${escapeHtml(err.message)}</div>`;
            return;
        }

        const wrap = document.createElement('div');
        wrap.className = 'pdf-canvas-wrap';

        contentEl.innerHTML = '';
        contentEl.appendChild(wrap);

        for (let n = 1; n <= pdfDoc.numPages; n++) {
            try {
                const page = await pdfDoc.getPage(n);
                const scale = wrap.clientWidth / page.getViewport({ scale: 1 }).width;
                const viewport = page.getViewport({ scale });
                const canvas = document.createElement('canvas');
                canvas.width = viewport.width;
                canvas.height = viewport.height;
                wrap.appendChild(canvas);
                await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
            } catch (err) {
                const errDiv = document.createElement('div');
                errDiv.className = 'viewer-text';
                errDiv.style.color = 'var(--color-accent-red)';
                errDiv.textContent = `Failed to render page ${n}: ${err.message}`;
                wrap.appendChild(errDiv);
            }
        }
    }

    async function renderXlsx(url) {
        contentEl.innerHTML = '<div class="viewer-text">Loading…</div>';

        let arrayBuffer;
        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            arrayBuffer = await res.arrayBuffer();
        } catch (err) {
            contentEl.innerHTML =
                `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to load spreadsheet: ${escapeHtml(err.message)}</div>`;
            return;
        }

        try {
            const workbook = XLSX.read(arrayBuffer, { type: 'array' });
            const sheets = workbook.SheetNames.map(name => ({
                name,
                html: XLSX.utils.sheet_to_html(workbook.Sheets[name], { editable: false })
            }));

            const container = document.createElement('div');
            container.className = 'viewer-xlsx';

            const tabBar = document.createElement('div');
            tabBar.className = 'viewer-xlsx-tabs';

            const sheetArea = document.createElement('div');
            sheetArea.className = 'viewer-xlsx-sheet';

            function showSheet(index) {
                sheetArea.innerHTML = sheets[index].html;
                tabBar.querySelectorAll('.viewer-xlsx-tab').forEach((tab, i) => {
                    tab.classList.toggle('viewer-xlsx-tab--active', i === index);
                });
            }

            sheets.forEach((sheet, i) => {
                const tab = document.createElement('button');
                tab.className = 'viewer-xlsx-tab';
                tab.textContent = sheet.name;
                tab.addEventListener('click', () => showSheet(i));
                tabBar.appendChild(tab);
            });

            container.appendChild(tabBar);
            container.appendChild(sheetArea);
            contentEl.innerHTML = '';
            contentEl.appendChild(container);

            showSheet(0);
        } catch (err) {
            contentEl.innerHTML =
                `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to render spreadsheet: ${escapeHtml(err.message)}</div>`;
        }
    }

    async function renderText(url, ext) {
        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            currentRawText = await res.text();
        } catch (err) {
            contentEl.innerHTML = `<div class="viewer-text" style="color:var(--color-accent-red)">Failed to load file: ${escapeHtml(err.message)}</div>`;
            return;
        }
        renderPreviewView(ext);
    }

    function sanitizeHtml(html) {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        doc.querySelectorAll('.download-btn').forEach(el => el.remove());
        return doc.documentElement.outerHTML;
    }

    function stripFrontMatter(text) {
        return text.replace(/^---[ \t]*\r?\n[\s\S]*?\r?\n---[ \t]*(\r?\n|$)/, '');
    }

    function renderPreviewView(ext) {
        contentEl.innerHTML = '';
        if (ext === 'md') {
            const div = document.createElement('div');
            div.className = 'viewer-text';
            const content = stripFrontMatter(currentRawText);
            div.innerHTML = (typeof MarkdownRenderer !== 'undefined')
                ? new MarkdownRenderer().render(content)
                : escapeHtml(content).replace(/\n/g, '<br>');
            contentEl.appendChild(div);
        } else if (ext === 'html' || ext === 'htm') {
            const iframe = document.createElement('iframe');
            iframe.sandbox = 'allow-same-origin';
            iframe.srcdoc = sanitizeHtml(currentRawText);
            iframe.title = 'HTML preview';
            contentEl.appendChild(iframe);
        } else {
            contentEl.appendChild(buildCodeElement(currentRawText));
        }
    }

    function renderCodeView() {
        contentEl.innerHTML = '';
        contentEl.appendChild(buildCodeElement(currentRawText));
    }

    function buildCodeElement(text) {
        const lines = text.split('\n');
        if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();

        const wrapper = document.createElement('div');
        wrapper.className = 'viewer-code-wrap';

        const nums = document.createElement('pre');
        nums.className = 'viewer-line-nums';
        nums.textContent = lines.map((_, i) => i + 1).join('\n');

        const code = document.createElement('pre');
        code.className = 'viewer-code';
        code.textContent = text;

        wrapper.appendChild(nums);
        wrapper.appendChild(code);
        return wrapper;
    }

    function getExt(url) {
        return url.split('.').pop().toLowerCase().split('?')[0].split('#')[0];
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    return { init, open: openViewer, close };
})();
