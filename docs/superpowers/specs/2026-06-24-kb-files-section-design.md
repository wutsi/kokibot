# KB Files Section — Design Spec

**Date:** 2026-06-24  
**Scope:** Frontend only — no backend or HTML changes required

---

## Problem

The Knowledge Base settings tab currently shows only two toggles (Enable KB, Exclusive Mode). Users have no way to see which files are ingested or upload new ones from the UI. The backend endpoints already exist; they are simply not surfaced.

---

## Goal

Add a "Files" section to the existing Knowledge Base settings tab that lets users:
1. View the list of ingested files (name, scope, keywords)
2. Upload new files for ingestion

---

## Affected Files

| File | Change |
|------|--------|
| `src/main/resources/static/js/settings.js` | Add Files section rendering + upload logic |

No changes to `settings.html`, backend controllers, or CSS beyond what already exists.

---

## UI Layout

```
┌─────────────────────────────────────────────────┐
│ Enable Knowledge Base          [ toggle ]        │
│ Exclusive Mode                 [ toggle ]        │
├─────────────────────────────────────────────────┤
│ Files                          [ Upload ]        │
│                                                  │
│  document.pdf                                    │
│  scope: Describes the API authentication flow…  │
│  [auth] [oauth] [jwt] [api]                      │
│                                                  │
│  report-q1.docx                                  │
│  scope: Q1 financial summary…                   │
│  [finance] [quarterly] [revenue]                 │
└─────────────────────────────────────────────────┘
```

The Files section is appended below the toggles inside the existing `kb-content` container. No sub-tabs, no columns — matches the single-column section pattern used by Heartbeat (settings block + instructions block).

---

## Data Flow

1. `renderKnowledgeBase()` renders the toggles section, then immediately calls `loadKBFiles()`
2. `loadKBFiles()` fetches `GET /assistants/{name}/knowledge-base/entries` and calls `renderKBFiles(entries)`
3. `renderKBFiles(entries)` renders the file list or an empty state
4. "Upload" button triggers a hidden `<input type="file">` (any file type accepted)
5. On file selected: `uploadKBFile(file)` posts to `POST /assistants/{name}/knowledge-base/upload` (multipart)
6. On success: re-fetches entries and re-renders the list
7. On HTTP 409 conflict: `Notifications.error("File already ingested")`
8. During upload: Upload button is disabled with a spinner

---

## Entry Display

Each file row shows:
- **Filename** — primary text
- **Scope** — smaller secondary text (truncated to one line)
- **Keywords** — rendered as tag chips

---

## States

| State | Display |
|-------|---------|
| Loading | Spinner + "Loading files…" |
| Empty | Icon + "No files ingested yet" |
| Loaded | File rows |
| Upload in progress | Button disabled + spinner |
| Upload success | `Notifications.success(...)` + list reloads |
| Upload conflict (409) | `Notifications.error("File already ingested")` |
| Upload error | `Notifications.error(...)` |

---

## JS Methods Added to `Settings`

| Method | Purpose |
|--------|---------|
| `loadKBFiles()` | Fetch `GET /entries`, call `renderKBFiles` |
| `renderKBFiles(entries)` | Render list or empty state into `#kb-files-list` |
| `uploadKBFile(file)` | POST multipart to `/upload`, reload on success |

`renderKnowledgeBase()` is extended to append the Files section HTML and wire the upload input after rendering the toggles.

---

## Out of Scope

- Delete / remove ingested files (future work)
- Drag-and-drop upload
- File preview or detail view
- Pagination of the file list
