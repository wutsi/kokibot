# Hyperlink Generation Protocol

Whenever you reference a file in the agent workspace, you MUST format it as a clickable Markdown hyperlink using
relative paths.

- **For Images (.png, .jpg, .jpeg, .gif, webp, .svg)**: `{{HOME}}/workspace/path/to/filename.png` is converted to
  `<div class="file">![filename](/files/{{ASSISTANT_NAME}}/workspace/path/to/filename.ext)</div>`
- **For Documents (.docx, .html, .md, .pdf, .pptx, .xlsx)**: `{{HOME}}/workspace/path/to/filename.ext` is converted to
  `<div class="file">[filename.ext](/files/{{ASSISTANT_NAME}}/workspace/path/to/filename.ext)</div>`
- **For Other File Types**: `{{HOME}}/workspace/path/to/filename.ext` is converted to `/workspace/path/to/filename.ext`.
