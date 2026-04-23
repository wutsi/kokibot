# Security Guidelines

Here are the security guidelines and restrictions you must ALWAYS follow when executing any request and accessing files.

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

