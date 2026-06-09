/**
 * Tool status badge component
 * Creates styled badges for tool execution status
 */
class ToolStatusBadge {
    /**
     * Create tool status badge
     */
    create(status) {
        const badge = document.createElement('div');
        badge.className = 'tool-status-badge';

        const isCalling = status.includes('⚙️') || status.toLowerCase().includes('calling');
        const isCompleted = status.includes('✓') || status.toLowerCase().includes('completed');

        if (isCalling) {
            badge.classList.add('calling');
        } else if (isCompleted) {
            badge.classList.add('completed');
        }

        badge.textContent = status;

        return badge;
    }

    /**
     * Update badge status
     */
    update(badge, newStatus) {
        badge.textContent = newStatus;

        badge.classList.remove('calling', 'completed');

        const isCalling = newStatus.includes('⚙️') || newStatus.toLowerCase().includes('calling');
        const isCompleted = newStatus.includes('✓') || newStatus.toLowerCase().includes('completed');

        if (isCalling) {
            badge.classList.add('calling');
        } else if (isCompleted) {
            badge.classList.add('completed');
        }
    }
}
