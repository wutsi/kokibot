/**
 * Reasoning section view management
 * Handles collapsible reasoning display and tool status badges
 */
class ReasoningView {
    constructor() {
        this.reasoningChunks = [];
        this.sectionManager = new ReasoningSection();
        this.badgeManager = new ToolStatusBadge();
    }

    /**
     * Reset state for new message
     */
    reset() {
        this.reasoningChunks = [];
    }

    /**
     * Append reasoning chunk
     */
    appendChunk(messageElement, chunk) {
        this.reasoningChunks.push(chunk);

        const contentDiv = messageElement.querySelector('.message-content');

        let reasoningSection = contentDiv.querySelector('.reasoning-section');
        if (!reasoningSection) {
            reasoningSection = this.sectionManager.create();
            contentDiv.insertBefore(reasoningSection, contentDiv.firstChild);
        }

        const reasoningContent = reasoningSection.querySelector('.reasoning-content');

        const currentBlock = this.getCurrentBlock(reasoningContent);
        const text = this.reasoningChunks.join('');
        currentBlock.innerHTML = this.renderReasoningText(text);

        reasoningContent.scrollTop = reasoningContent.scrollHeight;
    }

    /**
     * Add tool status badge
     */
    addToolStatus(messageElement, status) {
        const contentDiv = messageElement.querySelector('.message-content');

        let reasoningSection = contentDiv.querySelector('.reasoning-section');
        if (!reasoningSection) {
            reasoningSection = this.sectionManager.create();
            contentDiv.insertBefore(reasoningSection, contentDiv.firstChild);
        }

        const reasoningContent = reasoningSection.querySelector('.reasoning-content');

        const badge = this.badgeManager.create(status);
        reasoningContent.appendChild(badge);

        this.reasoningChunks = [];
    }

    /**
     * Get current reasoning content block
     */
    getCurrentBlock(reasoningContent) {
        const toolBadges = reasoningContent.querySelectorAll('.tool-status-badge');
        const lastChild = reasoningContent.lastElementChild;

        if (toolBadges.length > 0 && lastChild && lastChild.classList.contains('tool-status-badge')) {
            const newBlock = document.createElement('div');
            newBlock.className = 'reasoning-content-block';
            reasoningContent.appendChild(newBlock);
            return newBlock;
        } else if (lastChild && lastChild.classList.contains('reasoning-content-block')) {
            return lastChild;
        } else {
            const block = document.createElement('div');
            block.className = 'reasoning-content-block';
            reasoningContent.appendChild(block);
            return block;
        }
    }

    /**
     * Render reasoning text (escape and preserve newlines)
     */
    renderReasoningText(text) {
        if (!text) return '';

        const escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');

        return escaped.replace(/\r\n/g, '<br>').replace(/[\r\n]/g, '<br>');
    }
}
