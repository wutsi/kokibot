/**
 * Notification Manager
 * Displays toast notifications for errors, warnings, and success messages
 */
class NotificationManager {
    constructor() {
        this.container = null;
        this.notifications = new Map(); // id -> notification element
        this.init();
    }

    init() {
        // Create container if it doesn't exist
        this.container = document.getElementById('notification-container');
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.id = 'notification-container';
            this.container.className = 'notification-container';
            document.body.appendChild(this.container);
        }
    }

    /**
     * Show an error notification
     */
    error(message, options = {}) {
        return this.show(message, 'error', options);
    }

    /**
     * Show a warning notification
     */
    warning(message, options = {}) {
        return this.show(message, 'warning', options);
    }

    /**
     * Show a success notification
     */
    success(message, options = {}) {
        return this.show(message, 'success', options);
    }

    /**
     * Show an info notification
     */
    info(message, options = {}) {
        return this.show(message, 'info', options);
    }

    /**
     * Show a notification with optional retry action
     * @param {string} message - The notification message
     * @param {string} type - error|warning|success|info
     * @param {object} options - { duration, dismissible, retry }
     * @returns {string} notification ID
     */
    show(message, type = 'info', options = {}) {
        const {
            duration = type === 'error' ? 0 : 5000, // Errors persist, others auto-dismiss
            dismissible = true,
            retry = null // { label: 'Retry', callback: () => {} }
        } = options;

        const id = this.generateId();
        const notification = this.createNotification(id, message, type, dismissible, retry);

        this.notifications.set(id, notification);
        this.container.appendChild(notification);

        // Trigger animation
        setTimeout(() => notification.classList.add('show'), 10);

        // Auto-dismiss if duration is set
        if (duration > 0) {
            setTimeout(() => this.dismiss(id), duration);
        }

        return id;
    }

    /**
     * Create notification element
     */
    createNotification(id, message, type, dismissible, retry) {
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.dataset.id = id;

        // Icon
        const icon = document.createElement('div');
        icon.className = 'notification-icon';
        icon.innerHTML = this.getIcon(type);

        // Content
        const content = document.createElement('div');
        content.className = 'notification-content';

        const messageEl = document.createElement('div');
        messageEl.className = 'notification-message';
        messageEl.textContent = message;
        content.appendChild(messageEl);

        // Action buttons container
        const actions = document.createElement('div');
        actions.className = 'notification-actions';

        // Retry button
        if (retry) {
            const retryBtn = document.createElement('button');
            retryBtn.className = 'notification-btn notification-btn-primary';
            retryBtn.textContent = retry.label || 'Retry';
            retryBtn.addEventListener('click', () => {
                if (retry.callback) {
                    retry.callback();
                }
                this.dismiss(id);
            });
            actions.appendChild(retryBtn);
        }

        // Close button
        if (dismissible) {
            const closeBtn = document.createElement('button');
            closeBtn.className = 'notification-close';
            closeBtn.innerHTML = '&times;';
            closeBtn.title = 'Dismiss';
            closeBtn.addEventListener('click', () => this.dismiss(id));
            actions.appendChild(closeBtn);
        }

        notification.appendChild(icon);
        notification.appendChild(content);
        notification.appendChild(actions);

        return notification;
    }

    /**
     * Dismiss a notification
     */
    dismiss(id) {
        const notification = this.notifications.get(id);
        if (!notification) return;

        notification.classList.remove('show');
        notification.classList.add('hide');

        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
            this.notifications.delete(id);
        }, 300); // Match CSS transition
    }

    /**
     * Clear all notifications
     */
    clearAll() {
        this.notifications.forEach((_, id) => this.dismiss(id));
    }

    /**
     * Get icon SVG for notification type
     */
    getIcon(type) {
        const icons = {
            error: '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>',
            warning: '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>',
            success: '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>',
            info: '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>'
        };
        return icons[type] || icons.info;
    }

    /**
     * Generate unique notification ID
     */
    generateId() {
        return 'notif_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    }
}

// Create global instance
const Notifications = new NotificationManager();
