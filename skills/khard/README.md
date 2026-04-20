# Skill: khard

A command-line address book manager that syncs with CardDAV servers.

[![](https://img.shields.io/badge/github-repo-blue?logo=github)](https://github.com/lucc/khard)
[![Python](https://img.shields.io/badge/python-3.x-blue.svg?logo=phython)](https://python.org)

## What This Skill Provides

## Setup

### Installation

```bash
brew install khard
```

### Configuration

#### Step1: Create configuration file

```markdown
mkdir -p `~/.config/khard/`
touch `~/.config/khard/khard.conf`
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
- `editor` is set to `/usr/bin/true` to disable editing contacts in an editor. You can change this to your preferred
  text editor if you want to edit contact details directly.
- `merge_editor` is set to `/usr/bin/vimdiff` for resolving conflicts when syncing. You can change this to your
  preferred diff tool if you want to handle merge conflicts differently.
- `show_uids` is set to `yes` to display unique identifiers for contacts, which can be helpful for troubleshooting and
  ensuring you are editing the correct contact.
- `preferred_version` under `[vcard]` is set to `4.0` to use the latest vCard format, which is widely supported. You can
  change this if your CardDAV server requires a different version.
