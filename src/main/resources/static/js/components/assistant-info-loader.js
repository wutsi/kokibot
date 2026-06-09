/**
 * Assistant info loader
 * Fetches and displays agent name and description
 */
class AssistantInfoLoader {
    constructor(nameElement, descriptionElement) {
        this.nameElement = nameElement;
        this.descriptionElement = descriptionElement;
        this.formatter = new MessageFormatter();
    }

    /**
     * Load assistant info
     */
    async load(agentName) {
        this.nameElement.textContent = this.formatter.formatAgentName(agentName);

        try {
            const response = await fetch(`/assistants/${agentName}`);
            if (!response.ok) {
                console.warn('Failed to load assistant info, using defaults');
                return;
            }

            const data = await response.json();

            this.nameElement.textContent = this.formatter.formatAgentName(data.name);
            if (data.description) {
                this.descriptionElement.textContent = data.description;
            }
        } catch (error) {
            console.error('Error loading assistant info:', error);
        }
    }
}
