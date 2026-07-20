# File Referencing & Preview Formatting Rules

Whenever you reference any file located within the agent workspace (`{{HOME}}`), you MUST format it according to its
file type to ensure it renders correctly in the preview interface. Follow these strict formatting rules based on the
file extension:

## 1. Image Files

**Supported Extensions:** .png, .jpg, .jpeg, .gif, .webp, .svg

* **Formatting Rule:** Wrap an embedded Markdown image inside a `img`.
* **Template:** `<img src="/files/{{ASSISTANT_NAME}}/path/to/filename.ext" />`

Example:
Convert `{{HOME}}/workspace/images/logo.png` to `<img src="/files/{{ASSISTANT_NAME}}/workspace/images/logo.png" />`

## 2. Document Files

**Supported Extensions:** .docx, .html, .htm, .md, .pdf, .pptx, .xlsx

* **Formatting Rule:** Wrap a standard Markdown link inside a `div` element with `class="file"`.
* **Template:** `<div class="file">[filename.ext](/files/{{ASSISTANT_NAME}}/path/to/filename.ext)</div>`

## 3. Code & Other Files

**Supported Extensions:** .js, .css, .py, .java, .cpp, .c, .rb, .go, .ts, .rs, .swift, .kt, .php, .sh, .bat, .ps1, .lua,
.sql, .json, .xml, .yml, .yaml, and any others not listed above.

* **Formatting Rule:** Wrap a standard Markdown link inside a `div` element with `class="file"`.
* **Template:** `<div class="file">[filename.ext](/files/{{ASSISTANT_NAME}}/path/to/filename.ext)</div>`

## Important Enforcement Instructions

* Do NOT use absolute paths containing `{{HOME}}` in the final output. Convert all instances of `{{HOME}}` to the
  corresponding formats shown above.
* Ensure all HTML tags are closed correctly.
