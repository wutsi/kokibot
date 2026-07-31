/**
 * WebSocket connection manager
 * Wraps WebSocketClient with simpler event API
 */
class ConnectionManager {
    constructor(agentName) {
        this.agentName = agentName;
        this.wsClient = null;
        this.connectionErrorNotificationId = null;
        this.handlers = {
            onOpen: null,
            onClose: null,
            onQueued: null,
            onReasoningChunk: null,
            onToolStatus: null,
            onFinalResponse: null,
            onError: null
        };
    }

    /**
     * Connect to WebSocket
     */
    connect() {
        this.wsClient = new WebSocketClient(this.agentName);

        this.wsClient.on('Open', (event) => {
            // Clear any connection error notifications
            if (this.connectionErrorNotificationId) {
                Notifications.dismiss(this.connectionErrorNotificationId);
                this.connectionErrorNotificationId = null;
            }

            this.emit('open', event);
        });

        this.wsClient.on('Close', (event) => {
            // Show reconnection notification if not already shown
            if (!this.connectionErrorNotificationId) {
                this.connectionErrorNotificationId = Notifications.warning(
                    'Connection lost. Attempting to reconnect...',
                    { dismissible: false }
                );
            }

            this.emit('close', event);
        });

        this.wsClient.on('Error', (error) => {
            // Show persistent error notification
            if (!this.connectionErrorNotificationId) {
                this.connectionErrorNotificationId = Notifications.error(
                    'Connection error. Please check your network and refresh the page.',
                    { dismissible: true, duration: 0 }
                );
            }

            this.emit('error', error);
        });

        this.wsClient.on('Queued', (id) => {
            this.emit('queued', id);
        });

        this.wsClient.on('ReasoningChunk', (chunk, usage) => {
            this.emit('reasoningChunk', chunk, usage);
        });

        this.wsClient.on('ToolStatus', (status) => {
            this.emit('toolStatus', status);
        });

        this.wsClient.on('FinalResponse', (content, finishReason, conversationId) => {
            this.emit('finalResponse', content, finishReason, conversationId);
        });

        this.wsClient.on('ErrorMessage', (errorMessage) => {
            // Show LLM error message to user
            Notifications.error(`Assistant error: ${errorMessage}`, { duration: 0 });
            this.emit('error', errorMessage);
        });

        this.wsClient.connect();
    }

    /**
     * Send message
     */
    sendMessage(query, filePaths = [], conversationId = null) {
        this.wsClient.sendMessage(query, filePaths, conversationId);
    }

    /**
     * Check if connected
     */
    isConnected() {
        return this.wsClient &&
               this.wsClient.ws &&
               this.wsClient.ws.readyState === WebSocket.OPEN;
    }

    /**
     * Disconnect
     */
    disconnect() {
        if (this.wsClient) {
            this.wsClient.disconnect();
        }
    }

    /**
     * Register event handler
     */
    on(event, handler) {
        this.handlers[`on${event.charAt(0).toUpperCase() + event.slice(1)}`] = handler;
    }

    /**
     * Emit event to registered handlers
     */
    emit(event, ...args) {
        const handlerName = `on${event.charAt(0).toUpperCase() + event.slice(1)}`;
        if (this.handlers[handlerName]) {
            this.handlers[handlerName](...args);
        }
    }
}
