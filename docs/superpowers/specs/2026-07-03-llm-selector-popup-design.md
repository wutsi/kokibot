# LLM Selector Popup — Design Spec

**Date:** 2026-07-03  
**Status:** Approved

## Summary

Add a "Change" button to the Language Model settings tab that opens a modal allowing the user to switch the active LLM and model via a single-form dropdown UI.

## Context

The current LLM settings tab (`settings.js` / `displayLLMInfo()`) shows the active provider and model as read-only information. There is no way to change them from the UI.

Two backend endpoints already exist:
- `GET /llms?assistant={name}` — returns all available LLMs with their models (entries are `null` when no API key is configured for that provider)
- `POST /assistants/{name}/llm` — accepts `{ llm, model }` to switch the active LLM

## Design

### 1. "Change" Button

A small secondary-style button (`settings-action-btn settings-action-btn-secondary`) is added to the top-right corner of the `.llm-provider` card in `displayLLMInfo()`. It uses a pencil icon + "Change" label, consistent with edit buttons elsewhere in the settings page.

### 2. Modal

A centered overlay modal (new `.llm-modal-overlay` + `.llm-modal` CSS) with:

| Element | Detail |
|---|---|
| Title | "Change Language Model" |
| LLM dropdown | Populated from `GET /llms?assistant=...`, null entries filtered out. Pre-selected to current LLM. |
| Model dropdown | Populated from the selected LLM's `models` array. Updates when LLM selection changes. Pre-selected to current model (if the current model exists in the new list, else first model). |
| Footer | "Cancel" (secondary) + "Save" (primary) buttons |

### 3. Save Flow

1. `POST /assistants/{name}/llm` with `{ llm, model }`
2. On success: close modal, show success notification, force-reload the LLM tab (`onTabActivated('llm', true)`)
3. On error: show error notification, leave modal open

### 4. Changed Files

| File | Change |
|---|---|
| `src/main/resources/static/js/settings.js` | Add `openLLMSelector()`, `closeLLMModal()`, `saveLLMChange()`, modal HTML rendering, event bindings in `displayLLMInfo()` |
| `src/main/resources/static/css/settings.css` | Add modal overlay, modal container, modal header/body/footer styles |

### 5. Constraints

- Only LLMs with a configured API key (non-null entries from `GET /llms`) are shown
- The modal is closed by clicking Cancel, clicking the overlay, or pressing Escape
- No new files; changes are confined to `settings.js` and `settings.css`
