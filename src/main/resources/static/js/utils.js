/**
 * Utility functions
 */

function getAgentNameFromURL() {
    const params = new URLSearchParams(window.location.search);
    const agentFromURL = params.get('agent');
    if (agentFromURL) {
        localStorage.setItem('kokibot-agent', agentFromURL);
        return agentFromURL;
    }
    return localStorage.getItem('kokibot-agent') || 'thoth';
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function throttle(func, limit) {
    let inThrottle;
    return function (...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
