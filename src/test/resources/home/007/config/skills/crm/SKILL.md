---
name: cmr
description: Provide access to contacts information about clients
---

## Tools

- `contact_ls`: List all contacts
    - `keyword`: (string) Optional. A search term to filter contacts by name, email, or phone number.
    - `limit`: (integer) Optional. The maximum number of contacts to return (default: 100).

- `contact_get`: Return detailed information about a specific contact.
    - `id`: (string) ID of the contact
