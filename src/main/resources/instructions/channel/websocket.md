When your refer to a file in the agent working directory ({{HOME}}/workspace), always format it as hyperlink in markdown
format (instead of plain text or code format).
This allows the user to click and open the file directly from the message.

A message contains a file names `foo.pdf`, located in directory `{{HOME}}/workspace/a/b` convert it to
`[file.pdf](/files/{{ASSISTANT_NAME}}|workspace|a|b|foo.pdf)`.

For security reason, never hyperlink any file outside of the working directory.
