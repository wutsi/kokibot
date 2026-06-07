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
        this.handlers = {
            onOpen: null,
            onClose: null,
            onError: null,
            onReasoningChunk: null,
            onToolStatus: null,
            onFinalResponse: null,
            onErrorMessage: null
        };
    }

    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.host;
        const path = `/ws/${this.agentName}`;
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
            console.log('WebSocket connected');
            this.reconnectAttempts = 0;
            this.reconnectDelay = 1000;

            if (this.handlers.onOpen) {
                this.handlers.onOpen(event);
            }

            // Send queued messages
            this.flushMessageQueue();
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

            if (this.handlers.onClose) {
                this.handlers.onClose(event);
            }

            // Attempt reconnection
            this.attemptReconnect();
        };
    }

    handleMessage(response) {
        switch (response.type) {
            case 'REASONING_CHUNK':
                if (this.handlers.onReasoningChunk) {
                    this.handlers.onReasoningChunk(response.content);
                }
                break;

            case 'TOOL_STATUS':
                if (this.handlers.onToolStatus) {
                    this.handlers.onToolStatus(response.content);
                }
                break;

            case 'FINAL':
                if (this.handlers.onFinalResponse) {
                    this.handlers.onFinalResponse(response.content, response.finishReason);
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

    sendMessage(query, userId = null, filePaths = []) {
        const message = {
            query: query,
            userId: userId || this.generateUserId(),
            filePaths: filePaths
        };

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.warn('WebSocket not open, queueing message');
            this.messageQueue.push(message);

            // Try to reconnect if not connected
            if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
                this.connect();
            }
        }
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

    generateUserId() {
        // Get or create user ID from localStorage
        let userId = localStorage.getItem('kokibot_user_id');
        if (!userId) {
            userId = 'user_' + Math.random().toString(36).substring(2, 15);
            localStorage.setItem('kokibot_user_id', userId);
        }
        return userId;
    }

    disconnect() {
        if (this.ws) {
            this.ws.close();
        }
    }

    on(event, handler) {
        if (this.handlers.hasOwnProperty(`on${event}`)) {
            this.handlers[`on${event}`] = handler;
        }
    }
}
