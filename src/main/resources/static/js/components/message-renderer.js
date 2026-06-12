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
    updateFinalResponse(messageElement, text) {
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

        textDiv.innerHTML = this.markdownRenderer.render(text);

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
        textDiv.innerHTML = this.formatter.escapeAndPreserveNewlines(text);

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
     * Scroll chat to bottom
     */
    scrollToBottom() {
        this.container.scrollTop = this.container.scrollHeight;
    }
}
