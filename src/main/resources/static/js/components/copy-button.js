/**
 * Copy button component for copying message content to clipboard
 * Creates a copy icon that appears below message content
 */
class CopyButton {
    /**
     * Create copy button element
     */
    create() {
        const button = document.createElement('button');
        button.className = 'copy-button';
        button.setAttribute('aria-label', 'Copy to clipboard');
        button.title = 'Copy to clipboard';

        // SVG copy icon
        button.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
            </svg>
        `;

        return button;
    }

    /**
     * Setup copy functionality for a message element
     */
    setupCopy(messageElement) {
        const contentDiv = messageElement.querySelector('.message-content');
        if (!contentDiv) {
            return;
        }

        // Check if copy button already exists
        if (contentDiv.querySelector('.copy-button')) {
            return;
        }

        const button = this.create();

        button.addEventListener('click', async () => {
            await this.copyToClipboard(messageElement, button);
        });

        contentDiv.appendChild(button);
    }

    /**
     * Copy message text to clipboard
     */
    async copyToClipboard(messageElement, button) {
        try {
            const textDiv = messageElement.querySelector('.message-text');
            if (!textDiv) {
                return;
            }

            // Get the text content (plain text without HTML)
            const text = textDiv.innerText || textDiv.textContent;

            await navigator.clipboard.writeText(text);

            // Show success feedback
            this.showSuccess(button);
        } catch (err) {
            console.error('Failed to copy text:', err);
            this.showError(button);
        }
    }

    /**
     * Show success state
     */
    showSuccess(button) {
        const originalHTML = button.innerHTML;

        // Change to checkmark icon
        button.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
        `;
        button.classList.add('copied');

        // Reset after 2 seconds
        setTimeout(() => {
            button.innerHTML = originalHTML;
            button.classList.remove('copied');
        }, 2000);
    }

    /**
     * Show error state
     */
    showError(button) {
        button.classList.add('error');
        setTimeout(() => {
            button.classList.remove('error');
        }, 2000);
    }
}
