# KB Delete File — Design Spec

**Date:** 2026-06-24  
**Scope:** Frontend only — no backend changes required

---

## Goal

Add a delete button to each file row in the Knowledge Base Files section so users can remove ingested files.

---

## Affected Files

| File | Change |
|------|--------|
| `src/main/resources/static/js/settings.js` | Add delete button to file rows + `deleteKBFile()` method |

---

## UI Layout

```
┌──────────────────────────────────────────────┐
│  document.pdf                        [ 🗑 ]  │
│  scope: Describes the API auth flow…         │
│  [auth] [oauth] [jwt]                        │
└──────────────────────────────────────────────┘
```

Delete button sits inline at the right of each file row. During deletion the button is disabled and shows a spinner. No confirmation dialog.

---

## Data Flow

1. User clicks delete button on a row
2. Button disables, shows loading spinner
3. `deleteKBFile(filename)` calls `DELETE /assistants/{name}/knowledge-base/entries/{filename}`
4. On success: `Notifications.success(...)` + `loadKBFiles()` reloads the list
5. On error: `Notifications.error(...)`, button re-enables

---

## JS Changes

| Change | Details |
|--------|---------|
| `renderKBFiles(entries)` | Each row gets a `<button class="kb-delete-btn" data-filename="...">` with trash SVG |
| `deleteKBFile(filename)` | New method — `DELETE` fetch, 10s timeout, reload on success |

---

## Out of Scope

- Confirmation dialog
- Bulk delete
- Backend endpoint implementation
