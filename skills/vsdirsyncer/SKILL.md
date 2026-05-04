---
name: vdirsyncer
description: Command-line tool used to synchronize CalDAV and CardDAV calendars and address books between servers (e.g., Google, iCloud, Nextcloud) and a local folder.
requires:
    bins:
        - vdirsyncer
---

# Skill: vdirsyncer

Command-line tool used to synchronize CalDAV and CardDAV calendars and address books between servers (e.g., Google,
iCloud, Nextcloud) and a local folder.

---

## When to Use

Automatically invoke this skill when the user

- want to sync their calendar or contacts
- when they mention "sync calendar", "sync contacts", "vdirsyncer", "caldav", or "carddav".

---

### User Guide

```bash
# Sync all
vdirsyncer sync
```
