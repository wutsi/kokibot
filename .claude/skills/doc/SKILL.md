---
name: doc
description: "A skill for generating the project documents, such as README.md, docs/, etc."
parameters:
    -   name: type
        description: "The type of document to generate, e.g., README.md, CONTRIBUTING.md, etc."
        type: string
---

# Skill: doc

This skill is designed to generate various project documents, such as README.md, CONTRIBUTING.md, and more.
It takes a single parameter, `type`, which specifies the type of document to create.

## Parameters

- `type`: A string that indicates the type of document to generate. Examples include:
    - *readme*: To generate `README.md` following the guidelines provided in the `instructions/readme.md` file.
    - *docs*: To generate the long-term documentation following the instructions provided in the `instructions/docs.md`
      file.
    - Ignore any other value for `type` for now.
