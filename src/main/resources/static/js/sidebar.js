/**
 * Sidebar Toggle
 * Handles collapsing and expanding the sidebar
 */
const Sidebar = {
    sidebar: null,
    toggleButton: null,
    storageKey: 'kokibot_sidebar_collapsed',

    init() {
        this.setupElements();
        this.loadState();
        this.setupEventListeners();
    },

    setupElements() {
        this.sidebar = document.getElementById('sidebar');
        this.toggleButton = document.getElementById('sidebar-toggle');
    },

    loadState() {
        // Load saved state from localStorage (default: expanded)
        const isCollapsed = localStorage.getItem(this.storageKey) === 'true';
        if (isCollapsed) {
            this.sidebar.classList.add('collapsed');
        }
    },

    setupEventListeners() {
        this.toggleButton.addEventListener('click', () => {
            this.toggle();
        });
    },

    toggle() {
        const isCollapsed = this.sidebar.classList.toggle('collapsed');
        // Save state to localStorage
        localStorage.setItem(this.storageKey, isCollapsed.toString());
    },

    expand() {
        this.sidebar.classList.remove('collapsed');
        localStorage.setItem(this.storageKey, 'false');
    },

    collapse() {
        this.sidebar.classList.add('collapsed');
        localStorage.setItem(this.storageKey, 'true');
    }
};
