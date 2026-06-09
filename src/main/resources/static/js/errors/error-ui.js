/**
 * Error UI components
 * Manages toast notifications, inline errors, modals, and status bars
 */
export class ErrorUI {
    constructor() {
        this.activeToasts = [];
        this.activeModal = null;
        this.activeStatusBar = null;
        this.activeInlineErrors = new Map();

        this._ensureContainer();
    }

    _ensureContainer() {
        if (!document.getElementById('error-container')) {
            const container = document.createElement('div');
            container.id = 'error-container';
            container.className = 'error-container';
            document.body.appendChild(container);
        }
    }

    showToast(message, options = {}) {
        const {
            severity = 'error',
            duration = 5000,
            actions = []
        } = options;

        const toast = document.createElement('div');
        toast.className = `error-toast error-${severity}`;

        const icon = document.createElement('span');
        icon.className = 'error-icon';
        icon.textContent = this._getIcon(severity);

        const messageEl = document.createElement('div');
        messageEl.className = 'error-message';
        messageEl.textContent = message;

        const actionsEl = document.createElement('div');
        actionsEl.className = 'error-actions';

        actions.forEach(action => {
            const button = document.createElement('button');
            button.className = action.primary ? 'error-btn primary' : 'error-btn';
            button.textContent = action.label;
            button.onclick = () => {
                action.handler();
                this._removeToast(toast);
            };
            actionsEl.appendChild(button);
        });

        const closeBtn = document.createElement('button');
        closeBtn.className = 'error-close';
        closeBtn.innerHTML = '&times;';
        closeBtn.onclick = () => this._removeToast(toast);

        toast.appendChild(icon);
        toast.appendChild(messageEl);
        if (actions.length > 0) {
            toast.appendChild(actionsEl);
        }
        toast.appendChild(closeBtn);

        document.getElementById('error-container').appendChild(toast);
        this.activeToasts.push(toast);

        if (duration > 0) {
            setTimeout(() => this._removeToast(toast), duration);
        }
    }

    showInline(message, options = {}) {
        const { target, severity = 'error', actions = [] } = options;

        if (!target) {
            console.warn('Inline error requires target element');
            return;
        }

        this.removeInline(target);

        const inlineError = document.createElement('div');
        inlineError.className = `error-inline error-${severity}`;

        const content = document.createElement('div');
        content.className = 'error-inline-content';
        content.innerHTML = `
            <span class="error-icon">${this._getIcon(severity)}</span>
            <span class="error-message">${message}</span>
        `;

        if (actions.length > 0) {
            const actionsEl = document.createElement('div');
            actionsEl.className = 'error-actions';

            actions.forEach(action => {
                const button = document.createElement('button');
                button.className = 'error-btn-small';
                button.textContent = action.label;
                button.onclick = action.handler;
                actionsEl.appendChild(button);
            });

            content.appendChild(actionsEl);
        }

        inlineError.appendChild(content);

        target.parentNode.insertBefore(inlineError, target.nextSibling);
        this.activeInlineErrors.set(target, inlineError);
    }

    removeInline(target) {
        const existing = this.activeInlineErrors.get(target);
        if (existing) {
            existing.remove();
            this.activeInlineErrors.delete(target);
        }
    }

    showModal(message, options = {}) {
        const {
            title = 'Error',
            severity = 'error',
            actions = [],
            dismissible = true
        } = options;

        this.dismissModal();

        const overlay = document.createElement('div');
        overlay.className = 'error-modal-overlay';

        const modal = document.createElement('div');
        modal.className = `error-modal error-${severity}`;

        const header = document.createElement('div');
        header.className = 'error-modal-header';
        header.innerHTML = `
            <h3>${title}</h3>
            ${dismissible ? '<button class="error-modal-close">&times;</button>' : ''}
        `;

        const body = document.createElement('div');
        body.className = 'error-modal-body';
        body.innerHTML = `
            <div class="error-icon-large">${this._getIcon(severity)}</div>
            <p>${message}</p>
        `;

        const footer = document.createElement('div');
        footer.className = 'error-modal-footer';

        actions.forEach(action => {
            const button = document.createElement('button');
            button.className = action.primary ? 'error-btn primary' : 'error-btn';
            button.textContent = action.label;
            button.onclick = () => {
                action.handler();
                this.dismissModal();
            };
            footer.appendChild(button);
        });

        modal.appendChild(header);
        modal.appendChild(body);
        modal.appendChild(footer);
        overlay.appendChild(modal);

        document.body.appendChild(overlay);
        this.activeModal = overlay;

        if (dismissible) {
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    this.dismissModal();
                }
            });

            const closeBtn = header.querySelector('.error-modal-close');
            if (closeBtn) {
                closeBtn.onclick = () => this.dismissModal();
            }
        }
    }

    showStatusBar(message, options = {}) {
        const {
            severity = 'error',
            persistent = true,
            actions = []
        } = options;

        this.dismissStatusBar();

        const statusBar = document.createElement('div');
        statusBar.className = `error-status-bar error-${severity}`;

        const content = document.createElement('div');
        content.className = 'error-status-content';
        content.innerHTML = `
            <span class="error-icon">${this._getIcon(severity)}</span>
            <span class="error-message">${message}</span>
        `;

        if (actions.length > 0) {
            const actionsEl = document.createElement('div');
            actionsEl.className = 'error-actions';

            actions.forEach(action => {
                const button = document.createElement('button');
                button.className = 'error-btn-small';
                button.textContent = action.label;
                button.onclick = action.handler;
                actionsEl.appendChild(button);
            });

            content.appendChild(actionsEl);
        }

        if (!persistent) {
            const closeBtn = document.createElement('button');
            closeBtn.className = 'error-close';
            closeBtn.innerHTML = '&times;';
            closeBtn.onclick = () => this.dismissStatusBar();
            content.appendChild(closeBtn);
        }

        statusBar.appendChild(content);
        document.body.insertBefore(statusBar, document.body.firstChild);
        this.activeStatusBar = statusBar;
    }

    dismiss() {
        this.dismissToasts();
        this.dismissModal();
        this.dismissStatusBar();
        this.dismissAllInline();
    }

    dismissToasts() {
        this.activeToasts.forEach(toast => this._removeToast(toast));
    }

    dismissModal() {
        if (this.activeModal) {
            this.activeModal.remove();
            this.activeModal = null;
        }
    }

    dismissStatusBar() {
        if (this.activeStatusBar) {
            this.activeStatusBar.remove();
            this.activeStatusBar = null;
        }
    }

    dismissAllInline() {
        this.activeInlineErrors.forEach((error, target) => {
            error.remove();
        });
        this.activeInlineErrors.clear();
    }

    _removeToast(toast) {
        toast.classList.add('removing');
        setTimeout(() => {
            toast.remove();
            const index = this.activeToasts.indexOf(toast);
            if (index > -1) {
                this.activeToasts.splice(index, 1);
            }
        }, 300);
    }

    _getIcon(severity) {
        switch (severity) {
            case 'error': return '❌';
            case 'warning': return '⚠️';
            case 'info': return 'ℹ️';
            default: return '⚠️';
        }
    }
}
