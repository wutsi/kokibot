/**
 * WebSocket client for Kokibot
 * Handles connection, sending/receiving messages, and reconnection logic
 */
class WebSocketClient {
    constructor(agentName) {
        this.agentName = agentName;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 1000; // Start with 1 second
        this.messageQueue = [];
        this._watchdogInterval = null;
        this.handlers = {
            onOpen: null,
            onClose: null,
            onError: null,
            onQueued: null,
            onReasoningChunk: null,
            onToolStatus: null,
            onFinalResponse: null,
            onErrorMessage: null
        };
    }

    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.host;
        const path = `/ws?agent=${encodeURIComponent(this.agentName)}`;
        const wsUrl = `${protocol}//${host}${path}`;

        console.log(`Connecting to WebSocket: ${wsUrl}`);

        try {
            this.ws = new WebSocket(wsUrl);
            this.setupEventHandlers();
        } catch (error) {
            console.error('WebSocket connection error:', error);
            this.handleError(error);
        }
    }

    setupEventHandlers() {
        this.ws.onopen = (event) => {
            console.log('WebSocket connected: ', event);
            this.reconnectAttempts = 0;
            this.reconnectDelay = 1000;

            if (this.handlers.onOpen) {
                this.handlers.onOpen(event);
            }

            this.flushMessageQueue();
            this._startWatchdog();
        };

        this.ws.onmessage = (event) => {
            try {
                const response = JSON.parse(event.data);
                this.handleMessage(response);
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
            }
        };

        this.ws.onerror = (event) => {
            console.error('WebSocket error:', event);
            this.handleError(event);
        };

        this.ws.onclose = (event) => {
            console.log('WebSocket closed:', event.code, event.reason);
            this._stopWatchdog();

            if (this.handlers.onClose) {
                this.handlers.onClose(event);
            }

            this.attemptReconnect();
        };
    }

    handleMessage(response) {
        switch (response.type) {
            case 'QUEUED':
                if (this.handlers.onQueued) {
                    this.handlers.onQueued(response.id);
                }
                break;

            case 'REASONING_CHUNK':
                if (this.handlers.onReasoningChunk) {
                    this.handlers.onReasoningChunk(response.content, response.usage);
                }
                break;

            case 'TOOL_STATUS':
                if (this.handlers.onToolStatus) {
                    this.handlers.onToolStatus(response.content);
                }
                break;

            case 'FINAL':
                if (this.handlers.onFinalResponse) {
                    this.handlers.onFinalResponse(response.content, response.finishReason, response.conversationId);
                }
                break;

            case 'ERROR':
                if (this.handlers.onErrorMessage) {
                    this.handlers.onErrorMessage(response.message);
                }
                break;

            default:
                console.warn('Unknown message type:', response.type);
        }
    }

    sendMessage(query, filePaths = [], conversationId = null) {
        const message = {
            query: query,
            filePaths: filePaths,
            conversationId: conversationId,
        };

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(message));
            } catch (error) {
                console.error('Error sending message:', error);
                this.handleSendFailure(message, error);
            }
        } else {
            console.warn('WebSocket not open, queueing message');
            this.messageQueue.push(message);

            Notifications.warning(
                'Connection unavailable. Your message will be sent when reconnected.',
                { duration: 3000 }
            );

            if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
                this.connect();
            }
        }
    }

    handleSendFailure(message, error) {
        // Show error with retry option
        Notifications.error(`Failed to send message: ${error.message}`, {
            retry: {
                label: 'Retry',
                callback: () => {
                    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                        this.sendMessage(message.query, message.filePaths, message.conversationId);
                    } else {
                        this.messageQueue.push(message);
                        this.connect();
                    }
                }
            }
        });
    }

    flushMessageQueue() {
        while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            this.ws.send(JSON.stringify(message));
        }
    }

    attemptReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        this.reconnectAttempts++;
        console.log(`Reconnecting in ${this.reconnectDelay}ms (attempt ${this.reconnectAttempts})`);

        setTimeout(() => {
            this.connect();
        }, this.reconnectDelay);

        // Exponential backoff
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
    }

    handleError(error) {
        if (this.handlers.onError) {
            this.handlers.onError(error);
        }
    }

    disconnect() {
        this._stopWatchdog();
        if (this.ws) {
            this.ws.close();
        }
    }

    _startWatchdog() {
        this._stopWatchdog();
        // If onclose never fires (proxy silently drops connection), detect and reconnect
        this._watchdogInterval = setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.CLOSED) {
                console.warn('WebSocket found closed by watchdog, reconnecting');
                this._stopWatchdog();
                this.attemptReconnect();
            }
        }, 30000);
    }

    _stopWatchdog() {
        if (this._watchdogInterval) {
            clearInterval(this._watchdogInterval);
            this._watchdogInterval = null;
        }
    }

    on(event, handler) {
        if (this.handlers.hasOwnProperty(`on${event}`)) {
            this.handlers[`on${event}`] = handler;
        }
    }
}
