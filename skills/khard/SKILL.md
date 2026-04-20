---
name: khard
description: |
    This skill allows the agent to search, read, create, and modify contacts in the local CardDAV-synced address book using the khard CLI utility.
    It is optimized for non-interactive execution.
requires:
    bins:
        - khard
        - vdirsyncer
---

# Skill: khard

This skill allows the agent to search, read, create, and modify contacts in the local CardDAV-synced address book using
the khard CLI utility. It is optimized for non-interactive execution.

## General Usage

### Search for a contact

```bash
khard list -a <addressbook_name> -p "<search_term>"
```

The option `-p` (or `--parsable`) ensures the output is in a machine-readable format:
`<uid>\t<contact_name>\t<address_book_name>`

### Show full details of a specific contact

```bash
khard show -a <addressbook_name> --format yaml "<contact_uid>"
```

### Create a new contact

The agent MUST NOT use khard new. It MUST use khard post with explicit attribute assignments.

```bash
khard post -a <addressbook_name> fn="<Full Name>" email:<label>="<email>" phone:<label>="<number>"
```

### Update an existing contact

#### Update an attribute

```bash
EDITOR=true khard modify -a <addressbook_name> "<search_term>" <attribute>="<value>"
```

#### Append a note

```bash
EDITOR=true khard modify -a <addressbook_name> "<search_term>" note="<note_content>"
```

### Syncing

After any write operation (creation or modification), the agent SHOULD trigger a sync to push changes to the remote
server.

```bash
vdirsyncer sync
```

#### Help

View all available commands and options:

```bash
khard --help
```

View help for a specific command:

```bash
hard <command> --help
```

## Data Schema (VCard Mapping)

When interpreting or generating data, use the following key mappings:

- `fn`: Formatted Name (The display name).
- `n`: Family name; Given name; Additional names; Prefixes; Suffixes.
- `email`: Support for labels (e.g., `email:work`, `email:home`).
- `tel`: Support for labels (e.g., `phone:cell`, `phone:home`).
- `adr`: Address (e.g., `postbox;extended;street;locality;region;zip;country`).

## Error Handling & Constraints

- **Ambiguity:** If khard returns multiple results for a search, the agent MUST NOT attempt a modification. It must
  first narrow the search or use a unique identifier (UID).
- **Non-Interactive:** Never execute a command that results in a prompt. Always use --parsable or --yaml flags.
- **Safety:** Do not delete contacts, even if explicitly requested. Contact removal is a destructive action that should
  be handled manually by the user.
- The `addressbook_name` must correspond to a section defined in `~/.config/khard/khard.conf` (e.g.,
  `contacts_personal`).

## Example Agent Prompt to CLI

**Prompt:**

- `Add a new work contact for Alice Smith with email alice@example.com and sync the address book.`

**Agent Action:**

- `khard post -a contacts_work fn="Alice Smith" email:work="alice@example.com"`
- `vdirsyncer sync`

