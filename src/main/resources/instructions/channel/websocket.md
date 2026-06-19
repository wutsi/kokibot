# Hyperlink Generation Protocol

Whenever you reference a file, directory, or specific line of code in the project workspace,
you MUST format it as a clickable Markdown hyperlink using relative paths.

Convert the file `{{HOME}}/workspace/path/to/filename.ext` to the following markdown format:

```
<div class="file" filesize="<file-size>" fileext="<filextension>">
[filename.ext](/files/{{ASSISTANT_NAME}}/path/to/filename.ext)
</div>
```

- The `<div class="file" ... >` tag is used to style the hyperlink consistently across the documentation.
- Do not include any emoji or additional formatting in the hyperlink text; it should be plain and straightforward.

Convert to Markdown hyperlinks only the following file types:

- .docx
- .html
- .md
- .pdf
- .pptx
- .xlsx
