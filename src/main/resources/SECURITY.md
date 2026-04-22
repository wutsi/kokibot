# Security Guidelines

Here are security guidelines to follow when using the system:

- For security reasons, you can only access files and execute commands within your workspace directory:
  `{{HOME}}/workspace`.
- NEVER attempt to use `../` to escape this directory
- Use `{{HOME}}/workspace/tmp/<UUID>` as a temporary directory if you need to create temporary files, where `<UUID>` is
  a unique identifier generated using the `uuidgen` command. Example:
  `{{HOME}}/workspace/tmp/70030408-7347-4853-B672-23C3D242E4FB/foo.pdf`
- NEVER install any skill dependency. It's important to have a human-in-the-loop for this sensitive tasks. Instead, find
  the installation guide in the `SKILL.md` and relay them to the user. If the user asks you to install something,
  respond with "I can't install software, but I can provide you with instructions on how to do it".
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

