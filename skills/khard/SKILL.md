---
name: khard
description: This skill allows the agent to search, read, create, and modify contacts in the local CardDAV-synced address book using the khard CLI utility. It is optimized for non-interactive execution.
requires:
    bins:
        - khard
        - vdirsyncer
---

# Skill: khard

This skill allows the agent to search, read, create, and modify contacts in the local CardDAV-synced address book using
the khard CLI utility. It is optimized for non-interactive execution.

---

## Usage Guide

### Search for a contact

```bash
khard list -a <addressbook_name> -p "<search_term>"
```

The option `-p` (or `--parsable`) ensures the output is in a machine-readable format:
`<uid>\t<contact_name>\t<address_book_name>`

### Show full details of a specific contact

Show the details of a contact using its unique identifier (UID) in YAML format for easy parsing:

```bash
khard show -a <addressbook_name> --format yaml "<uid>"
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

---

## Data Schema (VCard Mapping)

When interpreting or generating data, use the following key mappings:

- `fn`: Formatted Name (The display name).
- `n`: Family name; Given name; Additional names; Prefixes; Suffixes.
- `email`: Support for labels (e.g., `email:work`, `email:home`).
- `tel`: Support for labels (e.g., `phone:cell`, `phone:home`).
- `adr`: Address (e.g., `postbox;extended;street;locality;region;zip;country`).

---

## Error Handling & Constraints

- **Ambiguity:** If khard returns multiple results for a search, the agent MUST NOT attempt a modification. It must
  first narrow the search or use a unique identifier (UID).
- **Non-Interactive:** Never execute a command that results in a prompt. Always use --parsable or --yaml flags.
- **Safety:** Do not delete contacts, even if explicitly requested. Contact removal is a destructive action that should
  be handled manually by the user.
- The `addressbook_name` must correspond to a section defined in `~/.config/khard/khard.conf` (e.g.,
  `contacts_personal`).

---

## Example Agent Prompt to CLI

**Prompt:**

- `Add a new work contact for Alice Smith with email alice@example.com and sync the address book.`

**Agent Action:**

- `khard post -a contacts_work fn="Alice Smith" email:work="alice@example.com"`
- `vdirsyncer sync`

---

## Installation Guide

### Installation

```bash
brew install khard
```

### Configuration

#### Step1: Create configuration file

```bash
mkdir -p ~/.config/khard/
touch ~/.config/khard/khard.conf
```

#### Step2: Edit configuration

The configuration will look like this:

```
[addressbooks]
[[personal]]
path = ~/.local/share/contacts/personal/card

[general]
debug = no
default_action = list
default_addressbook = personal
editor = /usr/bin/true
merge_editor = /usr/bin/vimdiff

[contact table]
display = first_name
group_by_addressbook = yes
reverse = no
show_nicknames = no
show_uids = yes
show_kinds = no
sort = last_name

[vcard]
vcards_per_file = one
preferred_version = 4.0
search_in_source_files = yes
```

**IMPORTANT:**

- `path` under `[addressbooks]` should point to the local directory where vdirsyncer syncs your contacts (e.g.,
  `~/.local/contacts/personal/card`).
- `default_addressbook` should match the name of the address book you want to use by default (e.g., `personal`).
- `show_uids` is set to `yes` to display unique identifiers for contacts, which can be helpful for troubleshooting and
  ensuring you are editing the correct contact.
- `editor` is set to `/usr/bin/true` to disable editing contacts in an editor. You can change this to your preferred
  text editor if you want to edit contact details directly.
- `merge_editor` is set to `/usr/bin/vimdiff` for resolving conflicts when syncing. You can change this to your
  preferred diff tool if you want to handle merge conflicts differently.
- For more details about `khard` configuration, refer [here](https://khard.readthedocs.io/en/stable/man/khard.conf.html)
