/**
 * Message input controller
 * Handles input state, events, and file attachments
 */
const SEND_ICON = '<svg height="24" viewBox="0 0 24 24" width="24"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>';
const STOP_ICON = '<svg height="24" viewBox="0 0 24 24" width="24"><rect x="6" y="6" width="12" height="12"/></svg>';

class InputController {
    constructor(inputElement, sendButton) {
        this.input = inputElement;
        this.sendButton = sendButton;
        this.handlers = {
            onSend: null,
            onStop: null
        };

        this.setupEventListeners();
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        this.sendButton.addEventListener('click', () => {
            if (this.sendButton.classList.contains('stop-mode')) {
                if (this.handlers.onStop) {
                    this.handlers.onStop();
                }
            } else {
                this.handleSend();
            }
        });

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
     * Enable input, reverting the send button to send mode
     */
    enable() {
        this.input.disabled = false;
        this.input.focus();
        this.sendButton.classList.remove('stop-mode');
        this.sendButton.innerHTML = SEND_ICON;
        this.sendButton.title = '';
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
     * Switch the send button into a clickable "Stop" button
     */
    showStopMode() {
        this.sendButton.classList.add('stop-mode');
        this.sendButton.innerHTML = STOP_ICON;
        this.sendButton.title = 'Stop';
        this.sendButton.disabled = false;
    }

    /**
     * Keep stop-mode styling but prevent further clicks while cancellation is in flight
     */
    disableStopButton() {
        this.sendButton.disabled = true;
    }

    /**
     * Register event handler
     */
    on(event, handler) {
        this.handlers[`on${event.charAt(0).toUpperCase() + event.slice(1)}`] = handler;
    }
}
