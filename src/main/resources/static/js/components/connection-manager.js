/**
 * WebSocket connection manager
 * Wraps WebSocketClient with simpler event API
 */
class ConnectionManager {
    constructor(agentName) {
        this.agentName = agentName;
        this.wsClient = null;
        this.handlers = {
            onOpen: null,
            onClose: null,
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
            this.emit('open', event);
        });

        this.wsClient.on('Close', (event) => {
            this.emit('close', event);
        });

        this.wsClient.on('Error', (error) => {
            this.emit('error', error);
        });

        this.wsClient.on('ReasoningChunk', (chunk, usage) => {
            this.emit('reasoningChunk', chunk, usage);
        });

        this.wsClient.on('ToolStatus', (status) => {
            this.emit('toolStatus', status);
        });

        this.wsClient.on('FinalResponse', (content, finishReason, contextLength) => {
            this.emit('finalResponse', content, finishReason, contextLength);
        });

        this.wsClient.on('ErrorMessage', (error) => {
            this.emit('error', error);
        });

        this.wsClient.connect();
    }

    /**
     * Send message
     */
    sendMessage(query, userId = null, filePaths = []) {
        this.wsClient.sendMessage(query, userId, filePaths);
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
