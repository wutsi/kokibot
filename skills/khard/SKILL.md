---
name: khard
description: Interfaces with the khard CLI to manage vCard-based contacts, enabling searching, creation, and retrieval of contact details for email or address book management.
requires:
    bins:
        - khard
        - vdirsyncer
    os:
        - linux
        - darwin
---

# Skill: khard

Interfaces with the khard CLI to manage vCard-based contacts, enabling searching, creation, and retrieval of contact
details for email or address book management.

---

## When to Use

- When need to access address book
- When need to search for contact details (phone, email, notes)
- When need to integrate contact retrieval into email composition

---

## Usage Guide

### Contact Retrieval

```bash
# Search Contacts: Find entries by name or keyword.
khard list [search_term]

# Display Details: Show full vCard information including phone numbers, emails, and notes.
khard show [search_term]

# Email Lookup: Specific format for integration with email clients.
khard email [search_term]
```




