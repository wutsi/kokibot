# Skill: vdirsyncer

`vdirsyncer` is a command-line tool for synchronizing calendars and addressbooks between a variety of servers and the
local filesystem.

[![](https://img.shields.io/badge/github-repo-blue?logo=github)](https://github.com/pimutils/vdirsyncer)
[![Python](https://img.shields.io/badge/python-3.x-blue.svg?logo=phython)](https://python.org)

## What This Skill Provides

- **Bidirectional Sync:** It doesn't just download data; it uploads changes too. If you delete an event in your
  terminal, vdirsyncer will delete it from your remote server the next time it runs.
- **Protocol Support:** It speaks the standard languages of the web for these types of data:
    - CalDAV: For calendars (Google, Fastmail, Apple).
    - CardDAV: For contacts/address books.
    - Filesystem: For local storage (storing events as .ics files and contacts as .vcf files).
- **Local Backend for CLI Tools:** It is almost always used as the "backend" to fetch data for lightweight,
  terminal-based applications that don't have built-in sync engines.
- **Offline Access:** Because it mirrors your remote data into local directories, you can view and edit your schedule or
  contacts even when you are not connected to the internet.

## Setup

### Installation

```bash
brew install vdirsyncer
```

### Configuration

#### Step1: Create configuration file

```markdown
mkdir -p ~/.config/vdirsyncer/
touch ~/.config/vdirsyncer/config
```

#### Step2: Edit configuration

- Status path in: `~/.cache/vdirsyncer/status/`
- Local contacts storage: `~/.local/contacts/`
- Remote CarDaV:
    - Server Configurations
        - [iCloud](https://vdirsyncer.pimutils.org/en/stable/tutorials/icloud.html)
        - [DavMail](https://vdirsyncer.pimutils.org/en/stable/tutorials/davmail.html)
        - [Google](https://vdirsyncer.pimutils.org/en/stable/tutorials/google.html)
        - [etc.](https://vdirsyncer.pimutils.org/en/stable/tutorials/google.html)
    - Store password securely using `password.fetch` with a command that outputs the password (e.g., `printenv` to read
      from an environment variable).

The configuration will look like this:

```
[general]
status_path = "~/.cache/vdirsyncer/status/"

[pair my_contacts]
a = "contacts_local"
b = "contacts_remote"
collections = ["from a", "from b"]

[storage contacts_local]
type = "filesystem"
path = "~/.local/share/contacts/personal"
fileext = ".vcf"

[storage contacts_remote]
type = "carddav"
url = "<remote-url>"
username = "<remote-username>"
password.fetch = ["command", "printenv", "VDIRSYNCHER_ICLOUD_PASSWORD"]
```

#### Step2: Initialize the local storage

1. Discover remove collections (calendars or address books) and create corresponding local folders:

```bash
vdirsyncer discover
```

2. Sync data from remote to local for the first time:
   **Sync**

```bash
vdirsyncer sync
```
