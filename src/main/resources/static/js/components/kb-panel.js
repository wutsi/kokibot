/**
 * Knowledge Base Panel
 * Displays KB files on the right side of the chat, visible only when KB is
 * enabled and has at least one ingested file.
 */
const KBPanel = {
    agentName: null,
    panel: null,
    filesList: null,

    init(agentName) {
        this.agentName = agentName;
        this.panel = document.getElementById('kb-panel');
        this.filesList = document.getElementById('kb-panel-files');
        if (!this.panel || !this.filesList) return;
        this.load();
    },

    async load() {
        try {
            const kbRes = await fetch(`/assistants/${this.agentName}/knowledge-base`);
            if (!kbRes.ok) return;
            const kb = await kbRes.json();
            if (!kb.enabled) return;

            const entriesRes = await fetch(`/assistants/${this.agentName}/knowledge-base/entries`);
            if (!entriesRes.ok) return;
            const entries = await entriesRes.json();
            if (!entries.length) return;

            this.render(entries);
        } catch (_) {}
    },

    render(entries) {
        this.filesList.innerHTML = '';
        entries.forEach(entry => this.filesList.appendChild(this.buildFileEl(entry)));
        this.panel.classList.remove('kb-panel--hidden');
    },

    buildFileEl(entry) {
        const a = document.createElement('a');
        a.className = 'kb-panel-file';
        a.href = entry.url;
        a.download = entry.filename;
        a.title = entry.scope || entry.filename;
        a.textContent = entry.filename;
        return a;
    },
};
