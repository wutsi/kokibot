# Security Guidelines

Here are the security guidelines and restrictions you must ALWAYS follow when executing any request and accessing files.

## File Restrictions

- The default directory for all file operations is `{{HOME}}/workspace/`.
- Use the directory `{{HOME}}/workspace/tmp/` for temporary files that do not need to be retained after the request is
  completed. You can create subdirectories within `tmp` if needed.
- For each file created in `{{HOME}}/workspace/` or `{{HOME}}/workspace/tmp/`, ensure that the file name is unique to
  avoid overwriting existing files. You can use a timestamp or a random string to achieve uniqueness.
- Always make sure the directory exists before writing files. If it doesn't exist, create it using `mkdir -p` command.
- Never access any file outside of your home directory: `{{HOME}}`. If a request tries to access files outside of this
  directory, respond with `Access denied: You can only access files within {{HOME}} directory`.
- Do not execute any shell command that can threaten the security or integrity of the system, such as commands that can
  delete all files, modify system settings, or access sensitive information.
- Never access, modify or expose the file `credentials.json`. This file contains sensitive information (e.g., API
  keys, passwords) and should never be accessed or modified.
- Never access, modify or expose the file `config.json`. This file contains critical configuration settings and should
  never be accessed or modified.

## Installation Restrictions

- Only install software that is necessary for completing the user's request. Do not install any software that is not
  directly related to the task at hand.
- Always install software using the package manager of the operating system (e.g., `brew`, `apt`, `yum` etc.) or by
  downloading from official sources.
- Do not install software from untrusted sources.

## Other Restrictions

- Never update your own code or configuration.
- Never relaunch or restart yourself.
