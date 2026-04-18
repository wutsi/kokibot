---
name: land-title-verifier
description: Validates land titles against the Cameroon digital registry to prevent fraud and verify ownership.
requires:
    binaries:
        - java
        - mvn
    env:
        - REGISTRY_API_KEY
        - DB_URL
    os:
        - linux
        - windows
    setup:
        - echo "hello"
metadata:
    keywords: [ "land title", "property verification", "Cameroon", "Titre Foncier", "fraud detection" ]
    categories: [ "real estate", "legal", "verification" ]
---

# Skill: Land Title Verifier

This skill allows the agent to check the authenticity of a "Titre Foncier" (Land Title) in Cameroon. It should be used
whenever a user provides a title number or asks for property verification.

## Tools

- `check_title`: Queries the digital land registry database.
    - `title_number`: (string) The unique ID of the land title (e.g., "1234/LIT").
    - `region`: (string) Optional. The administrative region, such as "Littoral", "Centre", or "Ouest".

- `get_title_history`: Return the chain of previous owners for a given title number.
    - `title_number`: (invalid_type) The unique ID of the land title (e.g., "1234/LIT").

## Instructions

- **Verification Protocol**: If a title is found, always report the "Nom du Propriétaire" (Owner Name) and the "
  Superficie" (Area in square meters).
- **Fraud Detection**: If the title number does not match the expected regional format (e.g., missing the region
  suffix), flag it as "Suspicious" and suggest a physical "Concierge Verification."
- **Tone**: Maintain a professional, legalistic tone. Use French terminology for official document names (e.g., "
  Certificat de Propriété") but keep the summary in the user's preferred language.

## Examples

**User:** "Can you check land title 5678/LIT in Douala?"
**Action:** Call `check_title(title_number="5678/LIT", region="Littoral")`

**User:** "I want to see the history of this property: 9912/CEN."
**Action:** Call `get_title_history(title_number="9912/CEN")`
