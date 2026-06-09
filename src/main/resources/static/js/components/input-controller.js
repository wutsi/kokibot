/**
 * Message input controller
 * Handles input state, events, and file attachments
 */
class InputController {
    constructor(inputElement, sendButton) {
        this.input = inputElement;
        this.sendButton = sendButton;
        this.handlers = {
            onSend: null
        };

        this.setupEventListeners();
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        this.sendButton.addEventListener('click', () => this.handleSend());

        this.input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.handleSend();
            }
        });

        this.input.addEventListener('input', () => {
            const hasText = this.input.value.trim().length > 0;
            this.sendButton.disabled = !hasText;
        });

        this.input.addEventListener('input', () => {
            this.input.style.height = 'auto';
            this.input.style.height = this.input.scrollHeight + 'px';
        });
    }

    /**
     * Handle send action
     */
    handleSend() {
        const text = this.input.value.trim();
        if (!text) return;

        const filesInfo = typeof FileUpload !== 'undefined'
            ? FileUpload.getUploadedFilesInfo()
            : [];

        if (this.handlers.onSend) {
            this.handlers.onSend(text, filesInfo);
        }

        this.clear();

        if (typeof FileUpload !== 'undefined') {
            FileUpload.clearUploadedFiles();
        }
    }

    /**
     * Clear input
     */
    clear() {
        this.input.value = '';
        this.input.style.height = 'auto';
        this.sendButton.disabled = true;
    }

    /**
     * Enable input
     */
    enable() {
        this.input.disabled = false;
        this.input.focus();
        this.sendButton.disabled = this.input.value.trim().length === 0;
    }

    /**
     * Disable input
     */
    disable() {
        this.input.disabled = true;
        this.sendButton.disabled = true;
    }

    /**
     * Register event handler
     */
    on(event, handler) {
        this.handlers[`on${event.charAt(0).toUpperCase() + event.slice(1)}`] = handler;
    }
}
