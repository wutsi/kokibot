# Skill: khal

A command-line address book manager that syncs with CardDAV servers.

[![](https://img.shields.io/badge/github-repo-blue?logo=github)](https://github.com/lucc/khal)
[![Python](https://img.shields.io/badge/python-3.x-blue.svg?logo=phython)](https://python.org)

## What This Skill Provides

## Setup

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
