---
name: khal
description: |
    This skill allows the agent to search, read, create, and modify events in the local CalDAV-synced calendar using the khal CLI utility.
    It is optimized for non-interactive execution.
requires:
    bins:
        - khal
        - vdirsyncer
---

# Skill: khal

This skill allows the agent to search, read, create, and modify events in the local CalDAV-synced calendar using the
khal CLI utility.
It is optimized for non-interactive execution.

---

## Usage Guide

### View Events

```bash
khal list                        # Today
khal list today 7d               # Next 7 days
khal list tomorrow               # Tomorrow
khal list 2026-01-15 2026-01-20  # Date range
khal list -a Work today          # Specific calendar
```

### Search

```bash
khal search "meeting"
khal search "dentist" --format "{start-date} {title}"
```

### Create Events

```bash
khal new 2026-01-15 10:00 11:00 "Meeting title"
khal new 2026-01-15 "All day event"
khal new tomorrow 14:00 15:30 "Call" -a Work
khal new 2026-01-15 10:00 11:00 "With notes" :: Description goes here
```

After creating, sync to push changes:

```bash
vdirsyncer sync
```

### Edit Events

Update an existing event without using the TUI, instead directly modifying the underlying `.ics` file. This is more
complex but allows for fully non-interactive automation.

**Protocol:**

1. Identify the file path: `khal search --format "{file-full-path}" "<search_term>"`
2. Read the `.ics` file using a Python script.
3. Modify the `ics.Calendar` object.
4. Overwrite the original `.ics` file.
5. Trigger sync: `vdirsyncer sync`.

### Delete Events

```bash
khal search --format "{file-full-path}" "<search_term>" | xargs rm
````

### Output Formats

For scripting:

```bash
khal list --format "{start-date} {start-time}-{end-time} {title}" today 7d
khal list --format "{uid} | {title} | {calendar}" today
```

Placeholders: `{title}`, `{description}`, `{start}`, `{end}`, `{start-date}`, `{start-time}`, `{end-date}`,
`{end-time}`, `{location}`, `{calendar}`, `{uid}`

---

## Rules & Contraints

- **Absolute Paths:** Never use `~` or `$HOME` in scripts.
- **Folding:** Ensure `.ics` files maintain the 75-octet line folding standard (automatic when using `ics` library).
- **Recurrence:** For creating recurrent events, the agent must generate a standard iCalendar string with an `RRULE` and
  use `khal import --batch`.
- **Syncing:** Always run `vdirsyncer sync` after any modification to ensure changes propagate to iCloud/iPhone.
- **Conflict Prevention:** Before editing, always run `vdirsyncer sync` to ensure the local file is the latest version
  from the server.

---

## Installation Guide

### Installation

```bash
brew install khal
```

### Configuration

#### Step1: Create configuration file

```markdown
mkdir -p ~/.config/khal/
touch ~/.config/khal/khal.conf
```

#### Step2: Edit configuration

The configuration will look like this:

```
[calendars]

  [[home]]
    path = ~/.local/share/calendar/events/home

```

**IMPORTANT:**

- `path` configured in `~/.config/khal/khal.conf` should point to the local directory where `vdirsyncer` syncs your
  events.
- For more details about `khal` configuration, refer [here](https://khal.readthedocs.io/en/latest/configure.html)
