# JavaScript Component Architecture

## Directory Structure

```
js/
├── components/              # Reusable UI components
│   ├── Core Components
│   ├── agent-selector.js    # Agent switching modal
│   ├── sidebar.js           # Navigation sidebar
│   ├── file-upload.js       # File upload manager
│   ├── notification-manager.js # Toast notifications
│   │
│   ├── Message Components
│   ├── message-renderer.js      # Message DOM management
│   ├── message-formatter.js     # Message text formatting
│   ├── markdown-renderer.js     # Markdown to HTML
│   │
│   ├── Chat Components
│   ├── input-controller.js      # Input field management
│   ├── connection-manager.js    # WebSocket wrapper
│   ├── assistant-info-loader.js # Agent metadata
│   │
│   ├── Reasoning Components
│   ├── reasoning-view.js        # Reasoning display logic
│   ├── reasoning-section.js     # Collapsible reasoning UI
│   ├── tool-status-badge.js     # Tool execution badges
│   │
│   ├── Display Components
│   ├── token-display.js         # Token usage display
│   └── context-gauge.js         # Context gauge (legacy)
│
├── chat-ui.js              # Main orchestrator
├── websocket-client.js     # WebSocket infrastructure
├── utils.js                # Utility functions
└── errors.bundle.js        # Error tracking
```

## Component Categories

### 1. Core Components
High-level UI features that users interact with directly.

#### **agent-selector.js**
- **Purpose**: Modal for switching between agents
- **Dependencies**: `Notifications` (notification-manager.js)
- **Exports**: `AgentSelector` singleton
- **Key Methods**:
  - `init(currentAgent)` - Initialize with current agent
  - `openModal()` - Show agent list modal
  - `loadAgents()` - Fetch available agents from API
  - `switchAgent(name)` - Switch to different agent

#### **sidebar.js**
- **Purpose**: Left navigation sidebar
- **Dependencies**: `Notifications`, `getAgentNameFromURL()` (utils.js)
- **Exports**: `Sidebar` singleton
- **Key Methods**:
  - `init()` - Initialize sidebar
  - `toggle()` - Collapse/expand sidebar
  - `handleNewChat()` - Clear history and reload
  - `handleHistory()` - Placeholder (coming soon)
  - `handleSettings()` - Placeholder (coming soon)

#### **file-upload.js**
- **Purpose**: File upload with validation and preview
- **Dependencies**: `Notifications`
- **Exports**: `FileUpload` singleton
- **Key Methods**:
  - `init(agentName)` - Initialize uploader
  - `uploadFiles(files)` - Upload multiple files
  - `getUploadedFilesInfo()` - Get uploaded file metadata
  - `clearUploadedFiles()` - Clear upload list

#### **notification-manager.js**
- **Purpose**: Toast notification system
- **Dependencies**: None (standalone)
- **Exports**: `Notifications` global singleton
- **Key Methods**:
  - `error(message, options)` - Show error notification
  - `warning(message, options)` - Show warning
  - `success(message, options)` - Show success
  - `info(message, options)` - Show info
  - `dismiss(id)` - Dismiss specific notification

---

### 2. Message Components
Handle message rendering and formatting.

#### **message-renderer.js**
- **Purpose**: DOM management for messages
- **Dependencies**: `MessageFormatter`, `MarkdownRenderer`
- **Exports**: `MessageRenderer` class
- **Key Methods**:
  - `addUserMessage(text, files)` - Add user message
  - `createAssistantMessage(id)` - Create placeholder
  - `updateFinalResponse(element, text)` - Update with final text

#### **message-formatter.js**
- **Purpose**: Format message text and metadata
- **Dependencies**: None
- **Exports**: `MessageFormatter` class
- **Key Methods**:
  - `escapeAndPreserveNewlines(text)` - Safe text rendering
  - `createFilesDisplay(files)` - Create file badges
  - `formatTime(date)` - Format timestamps

#### **markdown-renderer.js**
- **Purpose**: Convert markdown to HTML with syntax highlighting
- **Dependencies**: `marked.js` (CDN), `highlight.js` (CDN)
- **Exports**: `MarkdownRenderer` class
- **Key Methods**:
  - `render(text)` - Convert markdown to HTML

---

### 3. Chat Components
Handle chat infrastructure and user input.

#### **connection-manager.js**
- **Purpose**: Simplified WebSocket API
- **Dependencies**: `WebSocketClient`, `Notifications`
- **Exports**: `ConnectionManager` class
- **Key Methods**:
  - `connect()` - Establish WebSocket connection
  - `sendMessage(query, userId, files)` - Send message
  - `isConnected()` - Check connection status
  - `on(event, handler)` - Register event handler

#### **input-controller.js**
- **Purpose**: Manage input field and send button
- **Dependencies**: None
- **Exports**: `InputController` class
- **Key Methods**:
  - `enable()` - Enable input
  - `disable()` - Disable input
  - `clear()` - Clear input text
  - `on(event, handler)` - Register event handler

#### **assistant-info-loader.js**
- **Purpose**: Load and display agent metadata
- **Dependencies**: None
- **Exports**: `AssistantInfoLoader` class
- **Key Methods**:
  - `load(agentName)` - Fetch and display agent info

---

### 4. Reasoning Components
Handle LLM reasoning display.

#### **reasoning-view.js**
- **Purpose**: Manage reasoning section visibility and content
- **Dependencies**: `ReasoningSection`, `ToolStatusBadge`
- **Exports**: `ReasoningView` class
- **Key Methods**:
  - `appendChunk(element, chunk)` - Add reasoning text
  - `addToolStatus(element, status)` - Add tool badge
  - `reset()` - Clear state for new message

#### **reasoning-section.js**
- **Purpose**: Collapsible reasoning section UI
- **Dependencies**: None
- **Exports**: `ReasoningSection` class
- **Key Methods**:
  - `create()` - Create collapsible section
  - `setupToggle(section)` - Setup expand/collapse

#### **tool-status-badge.js**
- **Purpose**: Tool execution status badges
- **Dependencies**: None
- **Exports**: `ToolStatusBadge` class
- **Key Methods**:
  - `create(status)` - Create status badge

---

### 5. Display Components
Visual indicators and metrics.

#### **token-display.js**
- **Purpose**: Display LLM token usage
- **Dependencies**: None
- **Exports**: `TokenDisplay` class
- **Key Methods**:
  - `update(element, usage)` - Update token display
  - `reset()` - Clear display

#### **context-gauge.js** (Legacy)
- **Purpose**: Visual gauge for context window usage
- **Status**: Not currently loaded in index.html
- **Dependencies**: None
- **Exports**: `ContextGauge` singleton
- **Note**: Removed from UI but kept for potential Settings reintegration

---

## Non-Component Files

### **chat-ui.js** (Orchestrator)
- **Purpose**: Main application orchestrator
- **Pattern**: Facade/Mediator
- **Role**: Coordinates all components and manages application flow
- **Dependencies**: All components
- **Not a component**: Too high-level, manages component lifecycle

### **websocket-client.js** (Infrastructure)
- **Purpose**: Low-level WebSocket communication
- **Pattern**: Infrastructure layer
- **Role**: Protocol handling, reconnection, message parsing
- **Not a component**: Infrastructure service, not UI

### **utils.js** (Utilities)
- **Purpose**: Shared utility functions
- **Exports**: `getAgentNameFromURL()`, etc.
- **Not a component**: Stateless helpers

### **errors.bundle.js** (Third-party)
- **Purpose**: Error tracking (possibly Sentry/Rollbar)
- **Not a component**: External library

---

## Component Design Principles

### 1. **Single Responsibility**
Each component handles one specific UI concern.

### 2. **No Direct DOM Queries Outside Components**
- Components own their DOM elements
- Parent passes elements via constructor
- No global DOM selectors in non-component code

### 3. **Event-Driven Communication**
- Components expose `.on(event, handler)` API
- Use event emitters, not direct method calls
- Parent components subscribe to child events

### 4. **Dependency Injection**
- Pass dependencies via constructor
- No hidden global dependencies
- Makes testing easier

### 5. **Stateless Where Possible**
- Prefer stateless utility classes
- Store state in orchestrator (chat-ui.js)
- Components handle only UI state

---

## Initialization Order

**Loading order matters** for dependency resolution:

```javascript
// 1. External libraries
<script src="marked.js"></script>
<script src="highlight.js"></script>

// 2. Infrastructure
<script src="js/utils.js"></script>
<script src="js/websocket-client.js"></script>

// 3. Independent components (no dependencies)
<script src="js/components/notification-manager.js"></script>
<script src="js/components/message-formatter.js"></script>
<script src="js/components/markdown-renderer.js"></script>
<script src="js/components/reasoning-section.js"></script>
<script src="js/components/tool-status-badge.js"></script>
<script src="js/components/token-display.js"></script>

// 4. Dependent components (use components above)
<script src="js/components/reasoning-view.js"></script>
<script src="js/components/message-renderer.js"></script>
<script src="js/components/input-controller.js"></script>
<script src="js/components/connection-manager.js"></script>
<script src="js/components/assistant-info-loader.js"></script>

// 5. High-level components (use infrastructure)
<script src="js/components/agent-selector.js"></script>
<script src="js/components/sidebar.js"></script>
<script src="js/components/file-upload.js"></script>

// 6. Orchestrator (uses all components)
<script src="js/chat-ui.js"></script>
```

---

## Adding a New Component

### Checklist
1. ✅ Create file in `js/components/`
2. ✅ Add JSDoc header: `/** Component Name - Purpose */`
3. ✅ Export singleton or class (consistent with existing)
4. ✅ Implement `.init()` method (for singletons)
5. ✅ Use `.on(event, handler)` for event emitters
6. ✅ Add to `index.html` in correct dependency order
7. ✅ Update `COMPONENTS.md` documentation
8. ✅ Initialize in `DOMContentLoaded` handler

### Example Template
```javascript
/**
 * Example Component
 * Brief description of what this component does
 */
class ExampleComponent {
    constructor(element, dependencies) {
        this.element = element;
        this.deps = dependencies;
        this.handlers = {};
    }

    init() {
        this.setupElements();
        this.setupEventListeners();
    }

    setupElements() {
        // Cache DOM elements
    }

    setupEventListeners() {
        // Attach event listeners
    }

    on(event, handler) {
        this.handlers[event] = handler;
    }

    emit(event, ...args) {
        if (this.handlers[event]) {
            this.handlers[event](...args);
        }
    }

    destroy() {
        // Cleanup
    }
}
```

---

## Testing Components

### Unit Testing
Each component should be testable in isolation:

```javascript
// Test sidebar.js
describe('Sidebar', () => {
    beforeEach(() => {
        document.body.innerHTML = '<aside id="sidebar">...</aside>';
        Sidebar.init();
    });

    it('should toggle collapsed class', () => {
        Sidebar.toggle();
        expect(document.getElementById('sidebar').classList.contains('collapsed')).toBe(true);
    });
});
```

### Integration Testing
Test component interactions:

```javascript
// Test file-upload + notification-manager
it('should show error notification on upload failure', async () => {
    FileUpload.uploadFiles([largefile]);
    await waitFor(() => {
        expect(document.querySelector('.notification-error')).toBeTruthy();
    });
});
```

---

## Component Metrics

| Metric | Count |
|--------|-------|
| **Total Components** | 15 |
| **Core Components** | 4 |
| **Message Components** | 3 |
| **Chat Components** | 3 |
| **Reasoning Components** | 3 |
| **Display Components** | 2 |
| **Average LOC** | ~200 lines |
| **Largest Component** | `file-upload.js` (240 lines) |
| **Smallest Component** | `assistant-info-loader.js` (35 lines) |

---

## Migration History

### 2026-06-09: Component Organization
- **Moved** `sidebar.js` → `components/sidebar.js`
- **Moved** `file-upload.js` → `components/file-upload.js`
- **Moved** `agent-selector.js` → `components/agent-selector.js`
- **Moved** `context-gauge.js` → `components/context-gauge.js`
- **Updated** All component headers with consistent format
- **Updated** `index.html` script paths

**Rationale**: Consistent organization improves discoverability and follows established patterns.

---

## Future Improvements

### Priority 1
- [ ] Convert singleton pattern to classes (better testability)
- [ ] Add TypeScript definitions (or JSDoc types)
- [ ] Create component test suite

### Priority 2
- [ ] Add component lifecycle hooks (mount, unmount)
- [ ] Implement component registry (dynamic loading)
- [ ] Add hot module replacement for development

### Priority 3
- [ ] Bundle components (Webpack/Vite)
- [ ] Lazy load non-critical components
- [ ] Add component performance monitoring
