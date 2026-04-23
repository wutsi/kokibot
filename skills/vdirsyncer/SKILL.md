---
name: vdirsyncer
description: |
    Synchronize remote CalDAV (calendars) and CardDAV (contacts) resources with a local filesystem to enable offline access and integration with CLI tools like khal and khard.
requires:
    bins:
        - vdirsyncer
---

# Skill: vdirsyncer

Vdirsyncer is a command-line tool for synchronizing calendars and address books between a variety of servers and the
local filesystem.
The most popular usecase is to synchronize a server with a local folder and use a set of other programs to change the
local events and contacts.
Vdirsyncer can then synchronize those changes back to the server.

---

## Usage Guide

### Initialize & Discover

Finding new calendars or address books on a server.

```bash
vdirsyncer discover
```

### Synchronize

Synchronize all accounts between remote servers and local storage:

```bash
vdirsyncer sync
```

### Health Check

Validating the configuration and connectivity without moving data.

```bash
vdirsyncer check
```

### Repair

Fixing metadata or UID issues if the local database becomes de-synced.

```bash
vdirsyncer repair
```

---

## Installation Guide

### Installation

```bash
brew install vdirsyncer
```

### Configuration

#### Step1: Create configuration file

Create the configuration file.

```markdown
mkdir -p ~/.config/vdirsyncer/
touch ~/.config/vdirsyncer/config
```

#### Step2: Edit configuration

Edit the file `~/.config/vdirsyncer/config`.

Here is and example configuration for synchronizing contacts, calendar events, and reminders with a `iCloud` server:

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
url = "https://contacts.icloud.com/"
username = "<remote-username>@mac.com"
password.fetch = ["command", "printenv", "VDIRSYNCHER_ICLOUD_PASSWORD"]

# --- CALENDAR ---
[pair my_calendar]
a = "calendar_local"
b = "calendar_remote"
collections = ["from a", "from b"]

[storage calendar_local]
type = "filesystem"
path = "~/.local/share/calendar/events"
fileext = ".ics"

[storage calendar_remote]
type = "caldav"
url = "https://caldav.icloud.com/"
username = "<remote-username>@mac.com"
password.fetch = ["command", "printenv", "VDIRSYNCHER_ICLOUD_PASSWORD"]
item_types = ["VEVENT"]
```

- For more details about `vdirsync` configuration, refer [here](https://vdirsyncer.pimutils.org/en/stable/config.html)
- Lear more about supported remote servers:
    - [iCloud](https://vdirsyncer.pimutils.org/en/stable/tutorials/icloud.html)
    - [DavMail](https://vdirsyncer.pimutils.org/en/stable/tutorials/davmail.html)
    - [Google](https://vdirsyncer.pimutils.org/en/stable/tutorials/google.html)
    - [etc.](https://vdirsyncer.pimutils.org/en/stable/tutorials/google.html)

#### Step3: Initialize the local storage

Discover remote collections (calendars or address books) and sync with your local storage:

```bash
vdirsyncer discover
vdirsyncer sync
```

