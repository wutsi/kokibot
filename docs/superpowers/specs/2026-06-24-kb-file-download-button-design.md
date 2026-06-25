# KB File Download Button

## Summary

Add a download button to each file row in the Knowledge Base settings page. The button links directly to the file URL returned by the `/entries` API endpoint.

## Change

**File:** `src/main/resources/static/js/settings.js` — `renderKBFiles()` method

Each entry row currently shows a delete button (absolute-positioned, top-right). Add a download button to its left, using `entry.url` as the `href` with the HTML `download` attribute so the browser triggers a file-save dialog.

## Implementation

In `renderKBFiles()`, add an `<a>` tag styled as an icon button inside each `.channel-item` row:

```html
<a href="${entry.url}" download
   class="kb-download-btn"
   title="Download file"
   style="position:absolute;top:8px;right:36px;...">
  <!-- download SVG icon -->
</a>
```

- Positioned at `right: 36px` (8px gap + 16px icon + 12px = room left of the 8px-right delete button)
- No JS event listener needed — native `<a download>` behaviour
- `updateKBFields()` does NOT need to disable download buttons — downloading is read-only and should remain accessible regardless of KB enabled state

## Non-goals

- No server-side changes
- No new CSS file; inline style consistent with existing delete button pattern
