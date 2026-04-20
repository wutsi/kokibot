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

- *Strategy:* Use `conflict_resolution = "from a"` or `"from b"` in the pair config if total automation is required.

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
