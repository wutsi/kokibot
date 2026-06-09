/**
 * Context Gauge Component (Legacy - Not Currently Used)
 * Displays visual gauge for LLM context window usage
 * Shows current tokens, max tokens, and percentage with color warnings
 */
const ContextGauge = {
    agentName: null,
    valueElement: null,
    maxElement: null,
    percentageElement: null,
    progressPath: null,
    refreshInterval: null,
    maxContextLength: null,

    init(agentName) {
        this.agentName = agentName;
        this.setupElements();
        this.loadContextLength();
    },

    setupElements() {
        this.valueElement = document.getElementById('context-value');
        this.maxElement = document.getElementById('context-max');
        this.percentageElement = document.getElementById('context-percentage');
        this.progressPath = document.getElementById('gauge-progress');
    },

    async loadContextLength() {
        if (!this.agentName) {
            return;
        }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000); // 5s timeout

            const response = await fetch(
                `/assistants/${this.agentName}/context-length?channel-id=websocket`,
                { signal: controller.signal }
            );

            clearTimeout(timeoutId);

            if (!response.ok) {
                throw new Error(`Failed to load context (${response.status})`);
            }

            const data = await response.json();
            this.maxContextLength = data.max;
            this.updateGauge(data.value, data.max);
        } catch (error) {
            console.error('Error loading context length:', error);
            this.showError();

            // Only show notification if it's not a timeout (timeouts are less critical)
            if (error.name !== 'AbortError') {
                Notifications.warning(
                    'Failed to load context information. It will update after your next message.',
                    { duration: 5000 }
                );
            }
        }
    },

    updateContextLength(value) {
        // Update gauge with new context length value, keeping the same max
        if (this.maxContextLength) {
            this.updateGauge(value, this.maxContextLength);
        } else {
            // If max not loaded yet, fetch it
            this.loadContextLength();
        }
    },

    updateGauge(value, max) {
        // Update text values
        this.valueElement.textContent = this.formatSize(value);
        this.maxElement.textContent = `of ${this.formatSize(max)}`;

        // Calculate percentage
        const percentage = max > 0 ? Math.round((value / max) * 100) : 0;
        this.percentageElement.textContent = `${percentage}%`;

        // Update gauge arc
        // Arc length is approximately 251.2 (half circle with radius 80)
        const arcLength = 251.2;
        const progress = (percentage / 100) * arcLength;
        this.progressPath.setAttribute('stroke-dasharray', `${progress} ${arcLength}`);

        // Update color based on percentage
        this.progressPath.classList.remove('warning', 'danger');
        if (percentage >= 90) {
            this.progressPath.classList.add('danger');
        } else if (percentage >= 70) {
            this.progressPath.classList.add('warning');
        }
    },

    formatSize(bytes) {
        if (bytes === 0 || bytes === null || bytes === undefined) {
            return '0 B';
        }

        const kb = bytes / 1024;
        const mb = kb / 1024;
        const gb = mb / 1024;

        if (gb >= 1) {
            return `${gb.toFixed(1)} GB`;
        } else if (mb >= 1) {
            return `${mb.toFixed(1)} MB`;
        } else if (kb >= 1) {
            return `${kb.toFixed(0)} KB`;
        } else {
            return `${bytes} B`;
        }
    },

    showError() {
        this.valueElement.textContent = '--';
        this.maxElement.textContent = 'of --';
        this.percentageElement.textContent = '--%';
        this.progressPath.setAttribute('stroke-dasharray', '0 251.2');
    },

    refresh() {
        this.loadContextLength();
    },

    setAgent(agentName) {
        this.agentName = agentName;
        this.refresh();
    }
};
