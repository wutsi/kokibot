# File Referencing & Preview Formatting Rules

Whenever you reference any file located within the agent home directory, you MUST format it according to its
file type to ensure it renders correctly in the preview interface. Follow these strict formatting rules based on the
file extension:

## 1. Image Files

* **Supported Extensions:** .png, .jpg, .jpeg, .gif, .webp, .svg
* **Formatting Rule:** Wrap an embedded Markdown image inside a `img`.
* **Template:** `<img src="/files/{{ASSISTANT_NAME}}/path/to/filename.ext" />`

Example: `{{HOME}}/workspace/images/logo.png` to `<img src="/files/{{ASSISTANT_NAME}}/workspace/images/logo.png" />`

## 2. Document and Code Files

* **Supported Extensions:**
    * Documents: .docx, .html, .htm, .md, .pdf, .pptx, .xlsx
    * Code: .js, .css, .py, .java, .cpp, .c, .rb, .go, .ts, .rs, .swift, .kt, .php, .sh, .bat, .ps1, .lua, .sql, .json,
      .xml, .yml, .yaml
* **Formatting Rule:** Wrap a standard Markdown link inside a `div` element with `class="file"`.
* **Template:**
  `<div class="file" data-path="[file-path]">[filename.ext](/files/{{ASSISTANT_NAME}}/path/to/filename.ext)</div>`

Example: `{{HOME}}/workspace/logo.html` to
`<div class="file" data-path="{{HOME}}/workspace/logo.html">[logo.html](/files/{{ASSISTANT_NAME}}/workspace/logo.html)</div>`

## 3. Other Files

* **Formatting Rule:** Wrap the file path into a code block.

## Important Enforcement Instructions

* Do NOT use absolute paths containing `{{HOME}}` in the final output. Convert all instances of `{{HOME}}` to the
  corresponding formats shown above.
* Ensure all HTML tags are closed correctly.
