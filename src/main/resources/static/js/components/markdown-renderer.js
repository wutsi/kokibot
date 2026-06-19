/**
 * Markdown to HTML rendering
 * Configures marked.js and highlight.js
 */
class MarkdownRenderer {
    constructor() {
        this.configureMarked();
    }

    /**
     * Configure marked.js options
     */
    configureMarked() {
        if (typeof marked === 'undefined') return;

        marked.setOptions({
            breaks: true,
            gfm: true,
            html: true,
            headerIds: false,
            mangle: false,
            highlight: (code, lang) => {
                if (typeof hljs !== 'undefined' && lang) {
                    try {
                        return hljs.highlight(code, { language: lang }).value;
                    } catch (e) {
                        console.warn('Highlight.js error:', e);
                    }
                }
                return code;
            }
        });
    }

    /**
     * Render markdown to HTML
     */
    render(text) {
        if (typeof marked === 'undefined') {
            return this.escapeHtml(text).replace(/\n/g, '<br>');
        }

        try {
            const html = marked.parse(text);

            setTimeout(() => {
                if (typeof hljs !== 'undefined') {
                    document.querySelectorAll('pre code:not(.hljs)').forEach((block) => {
                        hljs.highlightElement(block);
                    });
                }
            }, 0);

            return html;
        } catch (error) {
            console.error('Error rendering markdown:', error);
            return this.escapeHtml(text);
        }
    }

    /**
     * Escape HTML for fallback
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}
