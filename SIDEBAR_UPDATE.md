# Sidebar Update Documentation

## Overview
The sidebar has been redesigned and moved from the right side to the left side of the application, with new navigation features replacing the context gauge.

## Changes Summary

### 1. **Position Change**
- **Before**: Right sidebar with context gauge
- **After**: Left sidebar with navigation menu

### 2. **Visual Layout**
```
┌─────────────────────────────────────────┐
│           Header (Agent Info)           │
├──────────┬──────────────────────────────┤
│          │                              │
│ Sidebar  │      Chat Container          │
│ (Left)   │      (Messages)              │
│          │                              │
│  • New   │                              │
│  • Hist  │                              │
│  • Set   │                              │
│          │                              │
└──────────┴──────────────────────────────┘
│         Input Area (Footer)             │
└─────────────────────────────────────────┘
```

### 3. **New Navigation Items**

| Item          | Icon    | Status      | Action                               |
|---------------|---------|-------------|--------------------------------------|
| **New Chat**  | ➕      | Active      | Clears history and starts fresh chat |
| **History**   | 🕐      | Disabled    | Coming soon - view past conversations |
| **Settings**  | ⚙️      | Disabled    | Coming soon - configure preferences  |

## Technical Changes

### Frontend Changes

#### **HTML** (`index.html`)
1. Moved sidebar before chat container in DOM order
2. Removed context gauge markup
3. Added navigation buttons with SVG icons
4. Removed `context-gauge.js` script loading

#### **CSS** (`css/chat.css`)
**Sidebar Positioning:**
```css
/* Key changes */
.sidebar {
    border-right: 1px solid var(--color-border-light); /* Was border-left */
    order: -1; /* Place before chat container */
}

.sidebar.collapsed {
    width: 56px; /* Was 48px */
}

.sidebar-toggle {
    right: 12px; /* Was left: 12px */
}
```

**New Navigation Styles:**
```css
.sidebar-nav-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-radius: 8px;
    /* Hover effects and disabled states */
}
```

**Responsive Behavior:**
- Desktop (>1024px): 280px wide sidebar on left
- Tablet (768-1024px): 240px wide sidebar
- Mobile (<768px): Full-width collapsible sidebar at top

#### **JavaScript** (`js/sidebar.js`)

**New Features:**
1. **New Chat Handler**
   - Shows confirmation dialog
   - Calls `/assistants/{name}/clear` API
   - Reloads page on success
   - Shows error notification on failure

2. **Placeholder Handlers**
   - History: Shows "coming soon" notification
   - Settings: Shows "coming soon" notification

**API Integration:**
```javascript
handleNewChat() {
    if (confirm('Start a new chat? Current conversation will be cleared.')) {
        fetch(`/assistants/${agentName}/clear`, { method: 'POST' })
            .then(response => {
                if (response.ok) {
                    window.location.reload();
                } else {
                    throw new Error('Failed to clear chat');
                }
            })
            .catch(error => {
                Notifications.error('Failed to start new chat. Please refresh the page manually.');
            });
    }
}
```

### Backend Changes

#### **AssistantController.kt**
Added new endpoint:

```kotlin
@PostMapping("/{name}/clear")
fun clear(
    @PathVariable name: String,
    @RequestParam("user-id", required = false) userId: String? = null,
    @RequestParam("channel-id", required = false) channelId: String? = null,
): ResponseEntity<Map<String, Any>> {
    val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
        ?: return ResponseEntity.notFound().build()

    val context = bootstrap.getContext()
    context.chatHistory.clear(userId, channelId)

    return ResponseEntity.ok(
        mapOf(
            "success" to true,
            "message" to "Chat history cleared"
        )
    )
}
```

**Endpoint Details:**
- **URL**: `POST /assistants/{name}/clear`
- **Parameters**:
  - `name` (path) - Agent name (required)
  - `user-id` (query) - Optional user identifier
  - `channel-id` (query) - Optional channel identifier
- **Response**: `{ "success": true, "message": "Chat history cleared" }`

## User Experience

### New Chat Flow
1. User clicks "New Chat" button
2. Confirmation dialog appears: "Start a new chat? Current conversation will be cleared."
3. If confirmed:
   - API call to clear chat history
   - Page reloads with empty chat
   - Success notification (implicit via reload)
4. If cancelled:
   - No action taken
   - Dialog closes

### Error Handling
- **Network failure**: Error notification with manual refresh suggestion
- **API error**: Error notification with retry option (via manual page refresh)
- **Agent not found**: 404 response from backend

### Visual Feedback
- Active buttons have hover effects (light gray background)
- Disabled buttons are semi-transparent (50% opacity)
- Icons change color on hover (blue accent)
- Toast notifications for placeholder features

## Accessibility

### Improvements
- ✅ Semantic HTML (`<nav>`, `<button>`)
- ✅ Descriptive button text (not icon-only)
- ✅ Disabled state properly managed
- ✅ Keyboard accessible (tab navigation)
- ✅ Confirmation dialogs for destructive actions

### Remaining TODOs
- ⚠️ Add `aria-label` to buttons
- ⚠️ Add `aria-disabled="true"` to disabled buttons
- ⚠️ Add focus indicators for keyboard navigation

## Removed Features

### Context Gauge
The context gauge has been completely removed:
- Removed from HTML markup
- Removed CSS styles (kept for backwards compatibility)
- Removed JavaScript initialization
- `context-gauge.js` no longer loaded

**Rationale:**
- Limited utility for average users
- Takes up valuable sidebar space
- Can be reimplemented in Settings if needed

## Mobile Responsiveness

### Desktop (>768px)
- Sidebar: 280px wide on left
- Collapses to: 56px (icon-only)
- Toggle button: Top-right of sidebar

### Mobile (<768px)
- Sidebar: Full-width at top
- Collapses to: 48px height
- Toggle button: Center-top
- Navigation items stack vertically

## Browser Compatibility

| Feature | Chrome | Firefox | Safari | Edge |
|---------|--------|---------|--------|------|
| CSS Flexbox Order | ✅ | ✅ | ✅ | ✅ |
| SVG Icons | ✅ | ✅ | ✅ | ✅ |
| Fetch API | ✅ | ✅ | ✅ | ✅ |
| Confirm Dialogs | ✅ | ✅ | ✅ | ✅ |

**Minimum versions:**
- Chrome 55+
- Firefox 52+
- Safari 10.1+
- Edge 79+

## Testing Checklist

### Desktop
- [ ] Sidebar appears on left side
- [ ] Sidebar collapses/expands smoothly
- [ ] Toggle button rotates correctly
- [ ] New Chat shows confirmation dialog
- [ ] New Chat clears history on confirm
- [ ] History button shows "coming soon" notification
- [ ] Settings button shows "coming soon" notification
- [ ] Hover effects work on all buttons
- [ ] Disabled buttons cannot be clicked

### Mobile
- [ ] Sidebar appears at top
- [ ] Sidebar collapses vertically
- [ ] Toggle button centered at top
- [ ] Navigation items readable on small screens
- [ ] Touch targets large enough (44x44px)

### Integration
- [ ] Agent selector still works
- [ ] File upload still works
- [ ] Chat messages display correctly
- [ ] WebSocket connection unaffected
- [ ] Notifications system unaffected

## Future Enhancements

### Priority 1
- [ ] Implement History feature
  - List past conversations
  - Search/filter by date or content
  - Load previous conversation
  - Export conversation as markdown/JSON

### Priority 2
- [ ] Implement Settings feature
  - Theme selection (light/dark/auto)
  - Font size adjustment
  - Notification preferences
  - Clear all data option
  - Export settings

### Priority 3
- [ ] Add conversation metadata to sidebar
  - Current token count
  - Message count
  - Conversation duration
- [ ] Add quick actions
  - Copy conversation
  - Export as PDF
  - Share conversation link

## Related Files

### Modified Files
- `/src/main/resources/static/index.html` - Updated sidebar markup
- `/src/main/resources/static/css/chat.css` - New sidebar styles
- `/src/main/resources/static/js/sidebar.js` - New navigation logic
- `/src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt` - Clear endpoint

### Removed References
- Context gauge initialization removed from `index.html`
- Context gauge script no longer loaded

### Unchanged Files
- `/src/main/resources/static/js/context-gauge.js` - Still exists (not loaded)
- Context gauge CSS - Still exists (for backwards compatibility)

## API Endpoints Summary

| Endpoint | Method | Purpose | Parameters |
|----------|--------|---------|------------|
| `/assistants` | GET | List all agents | - |
| `/assistants/{name}` | GET | Get agent details | name |
| `/assistants/{name}/context-length` | GET | Get context usage | name, user-id?, channel-id? |
| `/assistants/{name}/clear` | POST | Clear chat history | name, user-id?, channel-id? |

## Performance Impact

### Positive Changes
- ✅ Removed context gauge polling (less network overhead)
- ✅ Simplified JavaScript initialization (1 fewer module)
- ✅ Cleaner DOM structure (less complexity)

### Neutral Changes
- No change in bundle size (navigation HTML ≈ gauge HTML)
- No change in CSS size (styles kept for compatibility)

## Migration Notes

### For Developers
1. Context gauge is no longer initialized - remove any code that depends on `ContextGauge` global
2. Sidebar is now on the left - update any CSS that assumes right positioning
3. New Chat calls backend API - ensure endpoint is available

### For Users
- Chat history now persists until "New Chat" is clicked
- Context gauge information no longer visible (can be re-added to Settings)
- Sidebar moved to left (more familiar desktop app pattern)

## Rollback Plan

If issues arise, to revert to previous sidebar:
1. Restore `index.html` sidebar markup from git history
2. Restore `css/chat.css` sidebar styles from git history
3. Restore `js/sidebar.js` from git history
4. Re-add `ContextGauge.init()` in initialization script
5. Remove `/assistants/{name}/clear` endpoint (optional)

## Impact Assessment

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Sidebar Width (Desktop) | 280px right | 280px left | Position only |
| Sidebar Width (Collapsed) | 48px | 56px | +8px |
| Navigation Items | 0 | 3 | +3 |
| Network Requests (per page load) | Context gauge API | None | -1 request |
| DOM Elements | ~15 (gauge) | ~10 (nav) | -5 |
| JavaScript Modules | 1 (gauge + sidebar) | 1 (sidebar) | Same |

**Overall**: Cleaner, more intuitive, with foundation for future features.
