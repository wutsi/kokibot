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
     * marked.js does not process Markdown inside block-level HTML elements (CommonMark spec).
     * Pre-convert [text](url) links inside <div class="file"> to <a> tags before parsing.
     */
    preprocessFileDivs(text) {
        return text
            .replace(
                /(<div\s+class="file"[^>]*>)\s*!\[([^\]]*)\]\(([^)]+)\)\s*(<\/div>)/g,
                '$1<img src="$3" alt="$2">$4'
            )
            .replace(
                /(<div\s+class="file"[^>]*>)\s*\[([^\]]+)\]\(([^)]+)\)\s*(<\/div>)/g,
                '$1<a href="$3">$2</a>$4'
            );
    }

    /**
     * Extract LaTeX blocks before marked parses them (marked would mangle backslashes and dollar signs).
     * Returns { processed, blocks } where blocks holds the original LaTeX and display mode.
     */
    extractLatex(text) {
        const blocks = [];

        // Block LaTeX: $$...$$ or \[...\]
        let processed = text.replace(/\$\$([\s\S]*?)\$\$/g, (_, latex) => {
            blocks.push({ latex, display: true });
            return `\x00LATEX${blocks.length - 1}\x00`;
        });
        processed = processed.replace(/\\\[([\s\S]*?)\\\]/g, (_, latex) => {
            blocks.push({ latex, display: true });
            return `\x00LATEX${blocks.length - 1}\x00`;
        });

        // Inline LaTeX: $...$ (single $, not empty, no newline inside)
        processed = processed.replace(/\$([^\$\n]+?)\$/g, (_, latex) => {
            blocks.push({ latex, display: false });
            return `\x00LATEX${blocks.length - 1}\x00`;
        });

        // Inline LaTeX: \(...\)
        processed = processed.replace(/\\\((.+?)\\\)/g, (_, latex) => {
            blocks.push({ latex, display: false });
            return `\x00LATEX${blocks.length - 1}\x00`;
        });

        return { processed, blocks };
    }

    /**
     * Restore LaTeX placeholders in HTML with KaTeX-rendered output.
     */
    restoreLatex(html, blocks) {
        if (!blocks.length || typeof katex === 'undefined') return html;

        return html.replace(/\x00LATEX(\d+)\x00/g, (_, idx) => {
            const { latex, display } = blocks[parseInt(idx, 10)];
            try {
                return katex.renderToString(latex, { displayMode: display, throwOnError: false });
            } catch (e) {
                return display ? `$$${latex}$$` : `$${latex}$`;
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
            const { processed, blocks } = this.extractLatex(text);
            const html = marked.parse(this.preprocessFileDivs(processed));
            const rendered = this.restoreLatex(html, blocks);

            setTimeout(() => {
                if (typeof hljs !== 'undefined') {
                    document.querySelectorAll('pre code:not(.hljs)').forEach((block) => {
                        hljs.highlightElement(block);
                    });
                }
            }, 0);

            return rendered;
        } catch (error) {
            console.error('Error rendering markdown:', error);
            return this.escapeHtml(text);
        }
    }

    /**
     * Render user-typed text: plain text with newlines preserved, but LaTeX blocks rendered.
     */
    renderUserText(text) {
        const { processed, blocks } = this.extractLatex(text);
        const escaped = this.escapeHtml(processed).replace(/\n/g, '<br>');
        return this.restoreLatex(escaped, blocks);
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
