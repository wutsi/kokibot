/**
 * Context Length Gauge
 * Displays and updates the context length meter
 */
const ContextGauge = {
    agentName: null,
    valueElement: null,
    maxElement: null,
    percentageElement: null,
    progressPath: null,
    refreshInterval: null,

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
            const response = await fetch(`/assistants/${this.agentName}/context-length`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            this.updateGauge(data.value, data.max);
        } catch (error) {
            console.error('Error loading context length:', error);
            this.showError();
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
