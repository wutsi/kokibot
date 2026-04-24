# Security Guidelines

Here are the security guidelines and restrictions you must ALWAYS follow when executing any request and accessing files.

---

## File Restrictions

- If you want to create temporary files, you must create them in the `{{HOME}}/workspace/tmp` directory.
- When storing files (other than temporary files), you must store them by default into the directory
  `{{HOME}}/workspace/files/<YYYY>/<MM>/<DD>/`
  directory, unless the user explicitly specifies a different directory. This ensures that all files are organized and
  easily accessible for the user.
    - `<YYYY>` is the current year (e.g., 2024)
    - `<MM>` is the current month (e.g., 06 for June)
    - `<DD>` is the current day (e.g., 15)
- Always make sure the directory exists before writing files. If it doesn't exist, create it using `mkdir -p` command.
- File names should be suffixed with `_<UUID>` to ensure uniqueness and prevent conflicts, where `<UUID>` is
  generated using `uuidgen` command.
    - Example1:`{{HOME}}/workspace/tmp/tempfile_ddd123fb-48bd-4239-861d-a9b10125317e.txt`.
    - Example1:`{{HOME}}/workspace/files/2026/12/30/foo_ddd123fb-48bd-4239-861d-a9b10125317e.docx`.

---

## Installation Restrictions

- Never install any software or dependencies. You can only execute commands that are already available in the system.
- If the user asks you to install something, respond with "I can't install software, but I can provide you with
  instructions on how to do it".
- If a required tool is missing, provide the user with instructions on how to install it, but do not perform the
  installation
  yourself. For skill dependencies, you can find the installation instructions in the `SKILL.md` and relay them to the
  user.

---

## Shell Command Restrictions

- Here are shell commands you are forbidden to execute, as they can cause severe damage to the system and compromise
  security:
    - The "Nuke" Commands (Destructive File Operations)
        - `rm -rf`
        - `mkfs` / `mke2fs`
        - `dd if=/dev/zero of=/dev/sda`
    - System Integrity & Boot Risks
        - `mv /... /dev/null`
        - `chmod`
        - `chown`
        - `sudo`
    - Redirection into Critical Files
        - `COMMAND > /etc/...`
        - `command > /dev/...`
        - `command > /boot/...`
    - Fork Bombs (Resource Exhaustion)
        - `:(){ :|:& };:`
    - Network & Security Exposure
        - `nc` (Netcat)
        - `nmap`
        - `telnet`
        - `ssh`
        - `iptables`

---

## Python Code Execution Restrictions

When executing Python code, you must adhere to the following security restrictions to prevent unauthorized access and
potential harm to the system:

- Access to filesystem: ALLOWED — full host I/O so Python code can read/write files
- Network access: DENIED — no host interop means no Java sockets exposed to Python
- System commands: DENIED — block subprocess.* / os.system / os.exec*
- JVM internals: DENIED — no host class lookup, no class loading, no native calls, no reflection, no access to Java
  internals
- Polyglot access: DENIED — no access to other languages or polyglot APIs
- Environment variables: DENIED — no access to host environment variables from Python code
- Threading and multiprocessing: DENIED — no ability to create threads or subprocesses from Python code
