# Design: Markdown to HTML Conversion for Reasoning Text

**Date:** 2026-05-25  
**Status:** Approved  
**Owner:** Claude Code

## Overview

Enable markdown-to-HTML conversion for reasoning text in the WebSocket chat interface, providing consistent formatting between reasoning chunks and final responses.

## Problem Statement

The web interface currently displays reasoning text as plain text with HTML escaping, while the final response supports full markdown rendering with syntax highlighting. This creates an inconsistent user experience where code snippets, formatting, and lists appear unformatted in the reasoning section but formatted in the final response.

## Goals

1. **Consistency:** Reasoning and final response should use identical markdown rendering
2. **Simplicity:** Minimal code changes, reuse existing infrastructure
3. **Performance:** No noticeable performance degradation during streaming
4. **Maintainability:** Single source of truth for markdown rendering logic

## Non-Goals

- Server-side markdown rendering
- Custom markdown extensions
- Performance optimization of the markdown pipeline
- Changes to the markdown library configuration

## Architecture

### Current Implementation

**File:** `src/main/resources/static/js/chat-ui.js`

**Reasoning flow:**
```javascript
handleReasoningChunk(chunk) {
    this.reasoningChunks.push(chunk);
    // ... message element setup ...
    this.updateReasoningSection(assistantMessage, this.reasoningChunks);
}

updateReasoningSection(messageElement, chunks) {
    reasoningContent.innerHTML = chunks.map(chunk =>
        `<span class="reasoning-chunk">${escapeHtml(chunk)}</span>`
    ).join('');
}
```

**Final response flow:**
```javascript
updateFinalResponse(messageElement, text) {
    textDiv.innerHTML = this.renderMarkdown(text);
}

renderMarkdown(text) {
    // Uses marked.js with highlight.js integration
    marked.setOptions({ breaks: true, gfm: true, ... });
    return marked.parse(text);
}
```

### Proposed Solution

Replace `escapeHtml(chunk)` with `this.renderMarkdown(chunk)` in the `updateReasoningSection` method.

**Modified flow:**
```javascript
updateReasoningSection(messageElement, chunks) {
    reasoningContent.innerHTML = chunks.map(chunk =>
        `<div class="reasoning-chunk">${this.renderMarkdown(chunk)}</div>`
    ).join('');
}
```

**Key change:** Element wrapper changed from `<span>` to `<div>` to properly contain block-level markdown elements (code blocks, lists, headings).

## Components Affected

### 1. JavaScript Module: `chat-ui.js`

**Location:** `src/main/resources/static/js/chat-ui.js:194-210`

**Changes:**
- Line 205: Replace `escapeHtml(chunk)` with `this.renderMarkdown(chunk)`
- Line 205: Change `<span>` to `<div>` for proper block element support

**Before:**
```javascript
reasoningContent.innerHTML = chunks.map(chunk =>
    `<span class="reasoning-chunk">${escapeHtml(chunk)}</span>`
).join('');
```

**After:**
```javascript
reasoningContent.innerHTML = chunks.map(chunk =>
    `<div class="reasoning-chunk">${this.renderMarkdown(chunk)}</div>`
).join('');
```

### 2. CSS Styling (Optional Enhancement)

**Location:** `src/main/resources/static/css/chat.css`

**Potential additions** (if styling issues arise):
```css
.reasoning-chunk {
    display: block;
    margin-bottom: 0.5rem;
}

.reasoning-chunk:last-child {
    margin-bottom: 0;
}

/* Ensure code blocks fit in reasoning section */
.reasoning-content pre {
    max-width: 100%;
    overflow-x: auto;
}
```

## Data Flow

```
┌─────────────────────┐
│  Backend (Kotlin)   │
│  WebSocketChannel   │
└──────────┬──────────┘
           │ WebSocket message
           │ { type: "REASONING_CHUNK", content: "**bold** text" }
           ▼
┌─────────────────────┐
│ Frontend (JS)       │
│ WebSocketClient     │
└──────────┬──────────┘
           │ on('ReasoningChunk', chunk)
           ▼
┌─────────────────────┐
│ ChatUI              │
│ handleReasoningChunk│
└──────────┬──────────┘
           │ this.reasoningChunks.push(chunk)
           ▼
┌─────────────────────┐
│ ChatUI              │
│ updateReasoningSection│
└──────────┬──────────┘
           │ chunks.map(chunk => renderMarkdown(chunk))
           ▼
┌─────────────────────┐
│ marked.js           │
│ + highlight.js      │
└──────────┬──────────┘
           │ HTML output
           ▼
┌─────────────────────┐
│ DOM                 │
│ .reasoning-content  │
└─────────────────────┘
```

## Error Handling

The existing `renderMarkdown()` method already includes error handling:

```javascript
try {
    const html = marked.parse(text);
    return html;
} catch (error) {
    console.error('Error rendering markdown:', error);
    return escapeHtml(text);  // Fallback to escaped plain text
}
```

This ensures that if markdown parsing fails for any chunk, it falls back to safe HTML-escaped text rather than breaking the UI.

## Performance Considerations

**Markdown parsing overhead:**
- Each chunk is parsed independently as it arrives
- Typical chunk size: 10-500 characters
- Marked.js parsing: ~0.1-1ms per chunk on modern browsers
- Streaming typically 1-10 chunks per second
- **Impact:** Negligible (< 10ms total per response)

**Alternative considered:** Batch rendering on expand (Approach 2 from brainstorming)
- **Rejected because:** Adds complexity for minimal gain; users expect formatted text when expanding reasoning

## Testing Strategy

### Manual Testing Checklist

1. **Basic markdown:** Send query that triggers reasoning with `**bold**`, `*italic*`, `inline code`
2. **Code blocks:** Reasoning with triple-backtick code blocks and language hints
3. **Lists:** Reasoning with bullet points and numbered lists
4. **Mixed content:** Multiple chunks with different markdown elements
5. **Edge cases:**
   - Empty reasoning chunks
   - Plain text chunks (no markdown)
   - Chunks with special HTML characters (`<`, `>`, `&`)
6. **UI interaction:**
   - Expand/collapse reasoning section
   - Scroll within reasoning section
   - Multiple messages with reasoning

### Test Queries

```
Query 1: "Explain how to write a Python function with code examples"
Expected: Code blocks with syntax highlighting in reasoning

Query 2: "List the steps to set up a web server"
Expected: Formatted lists in reasoning

Query 3: "What is <script>alert('xss')</script>?"
Expected: HTML characters safely escaped
```

## Rollback Plan

If issues arise, revert the single line change:

```javascript
// Rollback: Change line 205 back to
reasoningContent.innerHTML = chunks.map(chunk =>
    `<span class="reasoning-chunk">${escapeHtml(chunk)}</span>`
).join('');
```

No database migrations, no backend changes, no configuration changes needed.

## Future Enhancements (Out of Scope)

1. **Streaming markdown rendering:** Incremental parsing of incomplete markdown
2. **Custom markdown extensions:** Kokibot-specific syntax (e.g., tool call badges)
3. **Diff highlighting:** Show reasoning changes between iterations
4. **Copy button:** Add copy-to-clipboard for reasoning code blocks

## Alternatives Considered

### Alternative 1: Server-Side Markdown Rendering
**Rejected:** Requires backend changes, increases payload size, more complex

### Alternative 2: Batch Rendering on Expand
**Rejected:** Adds complexity for minimal performance gain

### Alternative 3: Simplified Markdown Subset
**Rejected:** Inconsistent with final response rendering, confusing UX

## Success Criteria

1. ✅ Reasoning text displays markdown formatting (bold, italic, code, lists)
2. ✅ Code blocks in reasoning have syntax highlighting
3. ✅ No visual regression in final response rendering
4. ✅ Expand/collapse reasoning section works correctly
5. ✅ No console errors during markdown rendering
6. ✅ HTML injection attempts are safely escaped

## Dependencies

**Existing:**
- `marked.js` v11.1.1 (already loaded in `index.html:48`)
- `highlight.js` v11.11.1 (already loaded in `index.html:49`)

**New:** None

## Implementation Estimate

- **Code changes:** 1 line (2 minutes)
- **Testing:** 30 minutes
- **Documentation:** Included in this spec
- **Total:** ~35 minutes

## Deployment

No special deployment considerations. Change is frontend-only, takes effect on page refresh.
