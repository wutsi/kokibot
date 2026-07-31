/**
 * Message rendering and DOM management
 * Handles creating and updating message elements
 */
class MessageRenderer {
    constructor(container) {
        this.container = container;
        this.formatter = new MessageFormatter();
        this.markdownRenderer = new MarkdownRenderer();
        this.copyButton = new CopyButton();
    }

    /**
     * Add user message to chat
     */
    addUserMessage(text, filesInfo = []) {
        const messageDiv = this.createMessageElement('user', text, filesInfo);
        this.container.appendChild(messageDiv);
        this.scrollToBottom();
    }

    /**
     * Add a complete (non-streaming) assistant message — used for history rendering
     */
    addAssistantMessage(text) {
        const id = 'hist_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
        const element = this.createAssistantMessage(id);
        this.updateFinalResponse(element, text);
        return element;
    }

    /**
     * Create assistant message placeholder
     * @returns {HTMLElement} Message element with thinking avatar
     */
    createAssistantMessage(id) {
        const messageDiv = document.createElement('div');
        messageDiv.id = id;
        messageDiv.className = 'message assistant';

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar thinking';
        avatar.innerHTML = '<span class="thinking-dots"><span>.</span><span>.</span><span>.</span></span>';

        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'message-content-wrapper';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        contentWrapper.appendChild(contentDiv);
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentWrapper);

        this.container.appendChild(messageDiv);
        this.scrollToBottom();

        return messageDiv;
    }

    /**
     * Update final response text
     */
    updateFinalResponse(messageElement, text, finishReason = null) {
        const avatar = messageElement.querySelector('.message-avatar');
        if (avatar) {
            avatar.classList.remove('thinking');
            avatar.textContent = 'A';
        }

        const contentDiv = messageElement.querySelector('.message-content');

        let textDiv = contentDiv.querySelector('.message-text');
        if (!textDiv) {
            textDiv = document.createElement('div');
            textDiv.className = 'message-text';
            contentDiv.appendChild(textDiv);
        }

        textDiv.classList.toggle('cancelled', finishReason === 'CANCELLED');
        textDiv.innerHTML = this.markdownRenderer.render(text);
        this.addImageDownloadButtons(textDiv);
        this.addCodeCopyButtons(textDiv);

        let timestamp = contentDiv.querySelector('.message-timestamp');
        if (!timestamp) {
            timestamp = document.createElement('div');
            timestamp.className = 'message-timestamp';
            contentDiv.appendChild(timestamp);
        }
        timestamp.textContent = this.formatter.formatTime(new Date());

        // Add copy button
        this.copyButton.setupCopy(messageElement);

        this.scrollToBottom();
    }

    /**
     * Add error message
     */
    addErrorMessage(errorText) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = `Error: ${errorText}`;
        this.container.appendChild(errorDiv);
        this.scrollToBottom();
    }

    /**
     * Create message element (user or assistant)
     */
    createMessageElement(type, text, filesInfo = []) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = type === 'user' ? 'U' : 'A';

        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'message-content-wrapper';

        if (filesInfo.length > 0) {
            const filesDiv = this.formatter.createFilesDisplay(filesInfo);
            contentWrapper.appendChild(filesDiv);
        }

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        textDiv.innerHTML = this.markdownRenderer.renderUserText(text);

        const timestamp = document.createElement('div');
        timestamp.className = 'message-timestamp';
        timestamp.textContent = this.formatter.formatTime(new Date());

        contentDiv.appendChild(textDiv);
        contentDiv.appendChild(timestamp);

        contentWrapper.appendChild(contentDiv);

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentWrapper);

        // Add copy button for both user and assistant messages
        this.copyButton.setupCopy(messageDiv);

        return messageDiv;
    }

    /**
     * Wrap each <img> in a relative container and overlay a download button.
     */
    addImageDownloadButtons(textDiv) {
        textDiv.querySelectorAll('img').forEach(img => {
            const wrapper = document.createElement('div');
            wrapper.className = 'img-wrapper';

            const fileDiv = img.parentNode.classList.contains('file') ? img.parentNode : null;
            const anchor = fileDiv || img;
            anchor.parentNode.insertBefore(wrapper, anchor);
            wrapper.appendChild(anchor);

            const btn = document.createElement('a');
            btn.className = 'img-download-btn';
            btn.href = img.src;
            btn.download = img.src.split('/').pop().split('?')[0] || 'image';
            btn.title = 'Download';
            btn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zm-8 2V5h2v6h1.17L12 13.17 9.83 11H11zm-6 7h14v2H5z"/></svg>';
            wrapper.appendChild(btn);
        });
    }

    addCodeCopyButtons(textDiv) {
        textDiv.querySelectorAll('pre').forEach(pre => {
            const wrapper = document.createElement('div');
            wrapper.className = 'code-block-wrapper';
            pre.parentNode.insertBefore(wrapper, pre);
            wrapper.appendChild(pre);

            const btn = document.createElement('button');
            btn.className = 'code-copy-btn';
            btn.title = 'Copy';
            btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M16 1H4C3 1 2 2 2 3v14h2V3h12V1zm3 4H8C7 5 6 6 6 7v14c0 1 1 2 2 2h11c1 0 2-1 2-2V7c0-1-1-2-2-2zm0 16H8V7h11v14z"/></svg>';
            btn.addEventListener('click', () => {
                const code = pre.querySelector('code');
                navigator.clipboard.writeText(code ? code.innerText : pre.innerText).then(() => {
                    btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>';
                    setTimeout(() => {
                        btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M16 1H4C3 1 2 2 2 3v14h2V3h12V1zm3 4H8C7 5 6 6 6 7v14c0 1 1 2 2 2h11c1 0 2-1 2-2V7c0-1-1-2-2-2zm0 16H8V7h11v14z"/></svg>';
                    }, 2000);
                });
            });
            wrapper.appendChild(btn);
        });
    }

    /**
     * Scroll chat to bottom
     */
    scrollToBottom() {
        this.container.scrollTop = this.container.scrollHeight;
    }
}
