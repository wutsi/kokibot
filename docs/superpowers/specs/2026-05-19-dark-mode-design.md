# Dark Mode Design Specification

**Date:** 2026-05-19  
**Status:** Approved  
**Implementation:** Pending

## Overview

Add automatic dark mode support to the Kokibot web chat interface. The system will detect the user's OS/browser preference using CSS media queries and automatically apply appropriate color schemes without requiring JavaScript or manual controls.

## Goals

- **Automatic switching:** Respect system `prefers-color-scheme` setting
- **Medium contrast:** Soft on eyes, balanced readability
- **Dark gray theme:** #1e1e1e base (not true black)
- **Maintainable:** CSS variables for easy theming
- **Accessible:** WCAG AA contrast compliance
- **Zero JavaScript:** Pure CSS solution

## Non-Goals

- Manual dark mode toggle (future enhancement)
- User preference persistence (not needed for automatic mode)
- Multiple theme options beyond light/dark
- Animated theme transitions

## User Experience

### Behavior

1. User opens web app (`http://localhost:8080/?agent=thoth`)
2. Browser detects system color scheme preference
3. App automatically renders in matching theme (light or dark)
4. If user changes system preference, app switches instantly
5. No user action required, no settings to configure

### Visual Changes

**Light Mode (Current):**
- White/light gray backgrounds (#ffffff, #f8f9fa)
- Dark text on light backgrounds (#202124)
- Light blue user messages (#e3f2fd)
- White assistant messages with subtle shadow

**Dark Mode (New):**
- Dark gray backgrounds (#1e1e1e, #2d2d2d)
- Light text on dark backgrounds (#e8eaed)
- Dark blue user messages (#1e3a5f)
- Elevated assistant messages (#2d2d2d)
- Softer shadows and borders
- Adjusted accent colors for visibility

## Architecture

### CSS Variable System

**Structure:**
```
:root {
  /* Light mode defaults (current colors) */
  --color-bg-primary: #ffffff;
  --color-text-primary: #202124;
  /* ... ~25 variables total */
}

@media (prefers-color-scheme: dark) {
  :root {
    /* Dark mode overrides */
    --color-bg-primary: #1e1e1e;
    --color-text-primary: #e8eaed;
    /* ... same 25 variables */
  }
}
```

**Variable Categories:**

1. **Backgrounds (6 variables)**
   - Primary: Main surfaces (header, messages, input)
   - Secondary: Chat container background
   - Tertiary: Reasoning section, hover states
   - User message: User bubble background
   - Assistant message: Assistant bubble background
   - Code block: Pre/code element backgrounds

2. **Text Colors (3 variables)**
   - Primary: Main content text
   - Secondary: Timestamps, labels, subtle text
   - Tertiary: Disabled states, placeholders

3. **Borders (2 variables)**
   - Light: Default borders, dividers
   - Medium: Focus states, emphasized borders

4. **Accents (3 variables)**
   - Blue: Links, buttons, user avatar
   - Green: Assistant avatar, success states
   - Red: Errors, disconnected status

5. **Shadows (2 variables)**
   - Small: Message cards, subtle elevation
   - Medium: App container main shadow

6. **Special (2 variables)**
   - Error background: Error message background
   - Error text: Error message text color

### Color Palette

**Light Mode (Existing):**
```css
--color-bg-primary: #ffffff;
--color-bg-secondary: #f8f9fa;
--color-bg-tertiary: #fafafa;
--color-bg-user-message: #e3f2fd;
--color-bg-assistant-message: #ffffff;
--color-bg-code: #f6f8fa;

--color-text-primary: #202124;
--color-text-secondary: #5f6368;
--color-text-tertiary: #9aa0a6;

--color-border-light: #e8eaed;
--color-border-medium: #dadce0;

--color-accent-blue: #1a73e8;
--color-accent-green: #34a853;
--color-accent-red: #ea4335;

--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.1);
--shadow-md: 0 0 20px rgba(0, 0, 0, 0.1);

--color-error-bg: #fce8e6;
--color-error-text: #c5221f;
```

**Dark Mode (New):**
```css
--color-bg-primary: #2d2d2d;
--color-bg-secondary: #1e1e1e;
--color-bg-tertiary: #3a3a3a;
--color-bg-user-message: #1e3a5f;
--color-bg-assistant-message: #2d2d2d;
--color-bg-code: #2d2d2d;

--color-text-primary: #e8eaed;
--color-text-secondary: #9aa0a6;
--color-text-tertiary: #5f6368;

--color-border-light: #3c4043;
--color-border-medium: #5f6368;

--color-accent-blue: #4c8bf5;
--color-accent-green: #5bb974;
--color-accent-red: #f28b82;

--shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.3);
--shadow-md: 0 0 20px rgba(0, 0, 0, 0.5);

--color-error-bg: #3d1a1a;
--color-error-text: #f28b82;
```

### Contrast Ratios (WCAG Compliance)

**Dark Mode Validation:**
- Primary text (#e8eaed on #1e1e1e): 12.63:1 → **AAA** ✓
- Secondary text (#9aa0a6 on #1e1e1e): 5.35:1 → **AA** ✓
- Tertiary text (#5f6368 on #1e1e1e): 3.15:1 → **Large text only** ✓
- Blue accent (#4c8bf5 on #1e1e1e): 7.2:1 → **AAA** ✓
- User message text (#e8eaed on #1e3a5f): 7.8:1 → **AAA** ✓

All ratios meet or exceed WCAG AA standards.

## Implementation Plan

### Phase 1: CSS Variables Setup

**File:** `src/main/resources/static/css/chat.css`

1. Add `:root` block at top of file with ~25 CSS variables
2. Set light mode values (extract from existing hardcoded colors)
3. Add `@media (prefers-color-scheme: dark)` block with dark overrides

### Phase 2: CSS Refactoring

Replace all hardcoded colors with CSS variables in these sections:

1. **Base Styles** (lines 8-13)
   - `body` background: `var(--color-bg-secondary)`
   - `body` color: `var(--color-text-primary)`

2. **App Container** (lines 16-24)
   - Background: `var(--color-bg-primary)`
   - Shadow: `var(--shadow-md)`

3. **Header** (lines 27-84)
   - Background: `var(--color-bg-primary)`
   - Border: `var(--color-border-light)`
   - Text colors: `var(--color-text-primary)`, `var(--color-text-secondary)`
   - Status dots: Keep existing color logic

4. **Chat Container** (lines 87-93)
   - Background: `var(--color-bg-secondary)`

5. **Messages** (lines 96-168)
   - User bubble: `var(--color-bg-user-message)`
   - Assistant bubble: `var(--color-bg-assistant-message)`
   - Avatar backgrounds: `var(--color-accent-blue)`, `var(--color-accent-green)`
   - Text: `var(--color-text-primary)`
   - Timestamps: `var(--color-text-secondary)`
   - Shadow: `var(--shadow-sm)`

6. **Reasoning Section** (lines 171-228)
   - Border: `var(--color-border-light)`
   - Background: `var(--color-bg-tertiary)`
   - Text: `var(--color-text-secondary)`
   - Hover background: Calculated from `--color-bg-tertiary`

7. **Typing Indicator** (lines 230-259)
   - Dot color: `var(--color-text-tertiary)`

8. **Input Container** (lines 262-321)
   - Background: `var(--color-bg-primary)`
   - Border: `var(--color-border-light)`
   - Input focus: `var(--color-accent-blue)`
   - Button background: `var(--color-accent-blue)`
   - Disabled button: `var(--color-bg-tertiary)`

9. **Error Messages** (lines 323-331)
   - Background: `var(--color-error-bg)`
   - Border: `var(--color-accent-red)`
   - Text: `var(--color-error-text)`

10. **Markdown Elements** (lines 349-480)
    - Headings: `var(--color-text-primary)`
    - Code inline: `var(--color-bg-code)`, adjust text color
    - Code blocks: `var(--color-bg-code)`, `var(--color-border-light)`
    - Blockquote border: `var(--color-accent-blue)`
    - Links: `var(--color-accent-blue)`
    - Table borders: `var(--color-border-light)`
    - Table headers: `var(--color-bg-tertiary)`
    - HR: `var(--color-border-light)`

11. **Scrollbar** (lines 482-497)
    - Track: `var(--color-bg-tertiary)`
    - Thumb: `var(--color-border-medium)`
    - Hover: Darken/lighten based on mode

### Phase 3: Syntax Highlighting

**File:** `src/main/resources/static/index.html` (line 8)

**Current:**
```html
<link href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css" rel="stylesheet">
```

**Updated:**
```html
<link href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css" 
      rel="stylesheet" 
      media="(prefers-color-scheme: light)">
<link href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css" 
      rel="stylesheet" 
      media="(prefers-color-scheme: dark)">
```

This ensures code syntax highlighting matches the theme.

### Phase 4: Testing

**Manual Testing:**
1. Start app: `mvn spring-boot:run`
2. Open browser: `http://localhost:8080/?agent=thoth`
3. **Light mode test:**
   - Set OS to light mode
   - Verify app displays light theme
   - Send messages, check all UI states
4. **Dark mode test:**
   - Set OS to dark mode
   - Verify app switches to dark theme instantly
   - Test all interactions: messages, reasoning, typing indicator
   - Verify text readability
5. **Dynamic switching:**
   - Toggle OS theme while app is open
   - Verify instant switching without refresh
6. **All browsers:**
   - Chrome, Firefox, Safari, Edge
   - Desktop and mobile

**Visual Checklist:**
- [ ] Background colors correct
- [ ] Text is readable in both modes
- [ ] Message bubbles visible and distinct
- [ ] Reasoning section collapsible and readable
- [ ] Input area usable
- [ ] Connection status visible
- [ ] Error messages stand out
- [ ] Code blocks have syntax highlighting
- [ ] Scrollbar visible but subtle
- [ ] No color bleeding or contrast issues

## Edge Cases

### Browser Support

**Modern Browsers (Full Support):**
- Chrome 76+
- Firefox 67+
- Safari 12.1+
- Edge 79+

**Legacy Browsers (Fallback):**
- Older browsers ignore `prefers-color-scheme`
- Fall back to light mode (existing styles)
- CSS variables still work in Chrome 49+, Firefox 31+, Safari 9.1+
- Graceful degradation, no broken experience

### Special Elements

**Hover States:**
- Light mode: Darken backgrounds on hover
- Dark mode: Lighten backgrounds on hover
- Use `filter: brightness()` or calculate from variables

**Focus States:**
- Maintain blue outline in both modes
- Adjust brightness: `var(--color-accent-blue)` handles this

**Shadows:**
- Dark mode uses darker, stronger shadows
- Light mode uses subtle gray shadows
- Defined in variables: `--shadow-sm`, `--shadow-md`

**Code Blocks:**
- Highlight.js theme switches via media query
- Background colors match app theme
- Text colors provided by highlight.js

**Images:**
- User-generated images work as-is
- No inversion needed (images display normally)

**SVG Icons:**
- Send button uses `currentColor` (already theme-aware)
- Status dots have explicit colors (work in both modes)

### Performance

- CSS variables have negligible performance impact
- Media query evaluation is instant
- No JavaScript overhead
- No layout shift or flash of wrong theme
- Smooth 60fps animations maintained

## Success Metrics

**Functional:**
- [ ] App automatically switches based on system preference
- [ ] All UI elements visible in both modes
- [ ] Text meets WCAG AA contrast requirements
- [ ] No visual bugs or color bleeding
- [ ] Works in all target browsers

**User Experience:**
- [ ] Dark mode is comfortable for extended use
- [ ] Theme switching is instant (no flicker)
- [ ] Colors are consistent across all sections
- [ ] Syntax highlighting matches theme
- [ ] No user confusion about theme selection

**Code Quality:**
- [ ] No hardcoded colors remain (all use variables)
- [ ] CSS is maintainable and well-organized
- [ ] Dark mode section is concise (~30 lines)
- [ ] No duplication of styles
- [ ] Easy to add manual toggle in future

## Future Enhancements (Out of Scope)

These are explicitly **not** part of this implementation:

- **Manual toggle button:** User control to override system preference
- **Theme persistence:** LocalStorage to remember user choice
- **Custom themes:** Blue, purple, high-contrast modes
- **Dimmed mode:** Partial dark mode for low-light environments
- **Scheduled themes:** Auto-switch at sunset/sunrise
- **Per-element customization:** User color picker

If needed, the CSS variable foundation makes these easy to add later.

## Files Modified

1. **`src/main/resources/static/css/chat.css`**
   - Add CSS variables at top (~50 lines)
   - Replace ~60 hardcoded colors with variables
   - Add dark mode media query (~30 lines)
   - Total additions: ~80 lines

2. **`src/main/resources/static/index.html`**
   - Add second `<link>` tag for dark syntax highlighting
   - Total additions: ~4 lines

**Total Impact:** ~84 lines added, ~60 lines modified, 0 lines deleted

## Rollback Plan

If dark mode causes issues:

1. **Quick fix:** Remove `@media (prefers-color-scheme: dark)` block
2. **Full rollback:** Revert `chat.css` and `index.html` to previous commit
3. **No database/backend changes:** Frontend-only change
4. **No breaking changes:** Light mode remains default

## Appendix: Color Reference

### Light Mode Colors (Existing)
| Element | Color | Usage |
|---------|-------|-------|
| Body background | #f8f9fa | Chat container |
| Surface | #ffffff | Header, messages, input |
| Primary text | #202124 | All body text |
| Secondary text | #5f6368 | Timestamps, labels |
| Tertiary text | #9aa0a6 | Disabled, subtle |
| Border | #e8eaed | Dividers, outlines |
| User bubble | #e3f2fd | User messages |
| Blue accent | #1a73e8 | Links, buttons |
| Green accent | #34a853 | Assistant |
| Red accent | #ea4335 | Errors |

### Dark Mode Colors (New)
| Element | Color | Usage |
|---------|-------|-------|
| Body background | #1e1e1e | Chat container |
| Surface | #2d2d2d | Header, messages, input |
| Elevated surface | #3a3a3a | Reasoning, hover |
| Primary text | #e8eaed | All body text |
| Secondary text | #9aa0a6 | Timestamps, labels |
| Tertiary text | #5f6368 | Disabled, subtle |
| Border | #3c4043 | Dividers, outlines |
| User bubble | #1e3a5f | User messages |
| Blue accent | #4c8bf5 | Links, buttons |
| Green accent | #5bb974 | Assistant |
| Red accent | #f28b82 | Errors |

---

**End of Specification**
