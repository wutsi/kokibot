---
name: apple-reminders
description: |
    Manage Apple Reminders via `remindctl`.
    Best for syncing tasks across iCloud devices (iPhone/Mac).
    Note: This creates persistent database entries, not temporary alarms.
keywords:
    - todo
    - task list
    - icloud reminders
requires:
    bins:
        - remindctl
    setup:
        - |
            if ! command -v remindctl &> /dev/null; then
                if ! command -v brew &> /dev/null; then
                    echo "Homebrew not found. Please install Homebrew or remindctl manually."
                    exit 1
                fi
                echo "remindctl not found. Attempting installation..."
                brew install steipete/tap/remindctl
            fi
            # Trigger a permission check early
            remindctl status || remindctl authorize

---

# Apple Reminders CLI (remindctl)

Use `remindctl` to manage Apple Reminders directly from the terminal.

## When to Use

✅ **USE this skill when:**

- Creating tasks that need to persist across the Apple ecosystem.
- Managing "To-Do" lists specifically within the Reminders app.
- The user asks "What do I have to do today?" (Checking due dates).
- Setting location-based or time-based alerts that should trigger on mobile devices.

## When NOT to Use

❌ **DON'T use this skill when:**

- **Calendar Events:** Use Apple Calendar for meetings/appointments.
- **Immediate Alarms:** For "set a timer for 5 minutes," use a local timer/cron tool; Reminders are for tasks, not
  stopwatch functions.
- **Ephemeral Notes:** Use Apple Notes or Notion (or any other tool) for long-form thoughts.
- **Team Project Management:** Use Jira or GitHub Issues unless the user specifically asks to "remind me" about a
  ticket.

## Common Commands

### View Reminders

```bash
remindctl                    # Today's reminders
remindctl today              # Today
remindctl tomorrow           # Tomorrow
remindctl week               # This week
remindctl overdue            # Past due
remindctl all                # Everything
remindctl 2026-01-04         # Specific date
```

### Manage Lists

```bash
remindctl list               # List all lists
remindctl list Work          # Show specific list
remindctl list Projects --create    # Create list
remindctl list Work --delete        # Delete list
```

### Create Reminders

```bash
remindctl add "Buy milk"
remindctl add --title "Call mom" --list Personal --due tomorrow
remindctl add --title "Meeting prep" --due "2026-02-15 09:00"
```

### Complete/Delete

```bash
remindctl complete 1 2 3     # Complete by ID
remindctl delete 4A83 --force  # Delete by ID
```

### Output Formats

```bash
remindctl today --json       # JSON for scripting
remindctl today --plain      # TSV format
remindctl today --quiet      # Counts only
```

## Date Formats

Accepted by `--due` and date filters:

- `today`, `tomorrow`, `yesterday`
- `YYYY-MM-DD`
- `YYYY-MM-DD HH:mm`
- ISO 8601 (`2026-01-04T12:34:56Z`)

## Permissions

- Check status: `remindctl status`
- Request access: `remindctl authorize`_
