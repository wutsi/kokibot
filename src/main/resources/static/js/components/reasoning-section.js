/**
 * Collapsible reasoning section component
 * Creates and manages the toggle behavior
 */
class ReasoningSection {
    /**
     * Create reasoning section element
     */
    create() {
        const section = document.createElement('div');
        section.className = 'reasoning-section';
        section.innerHTML = `
            <div class="reasoning-header">
                <span class="reasoning-toggle expanded">▶</span>
                <span class="reasoning-title">Reasoning</span>
            </div>
            <div class="reasoning-content expanded">
                <div class="reasoning-content-block"></div>
            </div>
        `;

        this.setupToggle(section);

        return section;
    }

    /**
     * Setup toggle functionality
     */
    setupToggle(section) {
        const header = section.querySelector('.reasoning-header');
        const toggle = section.querySelector('.reasoning-toggle');
        const content = section.querySelector('.reasoning-content');

        header.addEventListener('click', () => {
            const isExpanded = toggle.classList.toggle('expanded');
            content.classList.toggle('expanded', isExpanded);
        });
    }

    /**
     * Expand section
     */
    expand(section) {
        const toggle = section.querySelector('.reasoning-toggle');
        const content = section.querySelector('.reasoning-content');

        toggle.classList.add('expanded');
        content.classList.add('expanded');
    }

    /**
     * Collapse section
     */
    collapse(section) {
        const toggle = section.querySelector('.reasoning-toggle');
        const content = section.querySelector('.reasoning-content');

        toggle.classList.remove('expanded');
        content.classList.remove('expanded');
    }

    /**
     * Check if expanded
     */
    isExpanded(section) {
        const toggle = section.querySelector('.reasoning-toggle');
        return toggle.classList.contains('expanded');
    }
}
