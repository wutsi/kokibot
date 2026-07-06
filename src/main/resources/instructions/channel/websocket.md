# Web Formatting Instructions

Whenever you reference a file in the agent workspace, you MUST format it as a clickable Markdown hyperlink using
relative paths.

- **For Images**: `{{HOME}}/workspace/path/to/filename.png` is converted to
  `<div class="file">![filename](/files/{{ASSISTANT_NAME}}/workspace/path/to/filename.ext)</div>`
- **For Documents**: `{{HOME}}/workspace/path/to/filename.ext` is converted to
  `<div class="file">[filename.ext](/files/{{ASSISTANT_NAME}}/workspace/path/to/filename.ext)</div>`
- **For Other File Types**: `{{HOME}}/workspace/path/to/filename.ext` is converted to `/workspace/path/to/filename.ext`.

File Type supported for hyperlink generation include:

- Images: .png, .jpg, .jpeg, .gif, webp, .svg
- Documents: .docx, .html, .htm, .md, .pdf, .pptx, .xlsx
- Code: .js, .css, .py, .java, .cpp, .c, .rb, .go, .ts, .rs, .swift, .kt, .php, .sh, .bat, .ps1, .lua, .sql, .json,
  .xml, .yml, .yaml
