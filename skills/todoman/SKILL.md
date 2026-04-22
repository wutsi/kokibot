---
name: todoman
description: |
    It enables the creation, modification, and retrieval of tasks across multiple lists (collections) with support for priorities, deadlines, and status tracking.
requires:
    bins:
        - todo
        - vdirsyncer
---

# SKILL: todoman (Task Management)

This skill provides a structured interface for managing tasks and agendas using the `todoman` CLI. It focuses on
maintaining a clean, actionable task list while ensuring data is synchronized with standard CalDAV stores.

---

## Actions

### List Tasks

Retrieve tasks based on specific filters.

```bash
todo list [--status <status>] [--priority <priority>] [location]
```

**Usage:** Use this to get an overview of pending items or to find a specific task ID.

### Create Task

Add a new entry to a specified collection.

```bash
todo new -l <collection> [-d <due_date>] [-p <priority>] <summary>
```

**Formatting:** Summary should be concise. Use ISO 8601 for dates (e.g., 2026-04-25).

### Edit/Update Task

Modify an existing task using its ID.

```bash
todo edit <id> [--summary <new_summary>] [--due <new_date>] [--priority <priority>] [--location <location>]
```

**Note:** This can be used to reschedule tasks or change their priority level.

### Complete Task

Mark a task as finished.

```bash
todo done <id>
```

**Usage:** Always confirm the task ID via todo list before marking as done.

### Delete Task

Permanently remove a task.

```bash
todo delete <id>
```

## Guidelines & Best Practices

- **Idempotency:** Before creating a new task, perform a todo list to check if a task with a similar summary already
  exists
  to avoid duplicates.
- **Priority Mapping:** * High: Use -p 1 for urgent blockers.
- **Medium:** Use -p 5 for standard tasks.
- **Low: Use -p 9 for "nice to have" items.
- **Collection Awareness:** Always verify the list location using todo lists if the target collection is not explicitly
  provided.
- **Status Management:** By default, todo list only shows pending tasks. Explicitly check --status COMPLETED if
  verifying
  past work.
- **Syncing:** After updating, sync to push changes with: `vdirsyncer sync`

## Example Interaction

- **User Request:**
    - "Remind me to review the documentation by Friday."
- **Agent Execution:**
    - **Search:** todo list (Check for existing review tasks).
    - **Action:** todo new -l Work -d 2026-04-24 "Review documentation"
    - **Output:** "I've added 'Review documentation' to your Work list, due this Friday."

---

## Installation Guide

### Installation

```bash
brew install todoman
```

### Configuration

#### Step1: Create configuration file

Create a configuration file.

```bash
mkdir -p ~/.config/todoman/
touch ~/.config/todoman/config.py
```

#### Step2: Edit configuration

Edit the file `~/.config/todoman/config.py`.

Its content should look like this:

```python

path = "~/.local/share/calendar/todos"
date_format = "%Y-%m-%d"
time_format = "%H:%M"
default_list = "personal"

```

**IMPORTANT:**

- Make sure that `default_list` corresponds to an existing collection in your CalDAV server.
- For more details about `todoman` configuration, refer [here](https://todoman.readthedocs.io/en/stable/configure.html)
