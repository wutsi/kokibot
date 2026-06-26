/**
 * Knowledge Base Panel
 * Displays KB files on the right side of the chat, visible only when KB is
 * enabled and has at least one ingested file.
 */
const KBPanel = {
    agentName: null,
    panel: null,
    filesList: null,
    count: null,
    viewAll: null,

    init(agentName) {
        this.agentName = agentName;
        this.panel = document.getElementById('kb-panel');
        this.filesList = document.getElementById('kb-panel-files');
        this.count = document.getElementById('kb-panel-count');
        this.viewAll = document.getElementById('kb-panel-view-all');
        if (this.viewAll) {
            this.viewAll.href = `/settings.html?agent=${encodeURIComponent(agentName)}&tab=knowledge-base`;
        }
        if (!this.panel || !this.filesList) return;
        this.load();
    },

    async load() {
        try {
            const kbRes = await fetch(`/assistants/${this.agentName}/knowledge-base`);
            if (!kbRes.ok) return;
            const kb = await kbRes.json();
            if (!kb.enabled) return;

            const entriesRes = await fetch(`/assistants/${this.agentName}/knowledge-base/entries?status=READY`);
            if (!entriesRes.ok) return;
            const entries = await entriesRes.json();
            if (!entries.length) return;

            this.render(entries);
        } catch (_) {}
    },

    render(entries) {
        this.filesList.innerHTML = '';
        entries.forEach(entry => this.filesList.appendChild(this.buildFileEl(entry)));
        if (this.count) this.count.textContent = `(${entries.length})`;
        this.panel.classList.remove('kb-panel--hidden');
    },

    buildFileEl(entry) {
        const a = document.createElement('a');
        a.className = 'kb-panel-file';
        a.href = entry.url;
        a.title = entry.scope || entry.filename;

        if (entry.type === 'FILE') {
            a.download = entry.filename;
            const dotIdx = entry.filename.lastIndexOf('.');
            const ext = dotIdx !== -1 ? entry.filename.slice(dotIdx + 1).toLowerCase() : '';
            const displayName = entry.displayName;
            if (ext) {
                const badge = document.createElement('span');
                badge.className = `message-file-extension file-extension-${ext}`;
                badge.textContent = ext;
                a.appendChild(badge);
            }
            const name = document.createElement('span');
            name.textContent = displayName;
            a.appendChild(name);
        } else if (entry.type === 'LINK') {
            a.target = '_blank';
            a.rel = 'noopener noreferrer';
            a.innerHTML = '<svg fill="currentColor" height="14" viewBox="0 0 24 24" width="14" style="flex-shrink:0"><path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/></svg>';
            const name = document.createElement('span');
            name.textContent = entry.displayName;
            a.appendChild(name);
        } else {
            a.textContent = entry.displayName;
        }

        return a;
    },
};
