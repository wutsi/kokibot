# Daily Log Protocol

## Initialization

At the start of the first session of a new day, check the directory `{{HOME}}/memory/history/`  for a file named
`YYYY-MM-DD.md`.

If it does not exist:

- **Create the file** `{{HOME}}/memory/history/YYYY-MM-DD.md`, with content the following content:
  `# Daily Log: [YYYY-MM-DD]`.
- **Carry over context:** Read the `Next Steps` and `Unresolved Blockers` from the previous day’s log (if available).
- **Set Objectives:** Define the primary goals for today.

## Structure & Formatting

Every log entry must follow this Markdown structure:

```markdown
# Daily Log: [YYYY-MM-DD]

## 🎯 Daily Objectives

- [ ] Primary Goal 1
- [ ] Primary Goal 2

## 📝 Activity Stream

### [HH:mm] - [Short Title]

**Intent:** What am I trying to solve?
**Action:** Brief summary of tool usage or code changes.
**Result:** Outcome of the action (Success/Failure/Error).

## 💡 Knowledge Capture

- [New facts discovered about the codebase or environment]
- [Configuration changes made to the local Mac Mini]

## 🚧 Blockers & Next Steps

- **Blockers:** What is currently stopping progress?
- **Next Steps:** Immediate tasks for the next session.
```

## Real-Time Updates

- **Frequency:** Update the log after every significant milestone, when switching tasks or each interaction with the
  user.
- **Atomic Entries:** Keep the "Activity Stream" concise. Focus on why a change was made rather than just what was
  changed.
- **Persistence:** Ensure the file is saved locally to maintain a "paper trail" for memory compaction and long-term
  retrieval.

## Memory Integration

- When a task is completed, update the `Daily Objectives` list with a completion mark.
- If a specific insight is gained, explicitly document it in the `Knowledge Capture` section so it can be promoted to
  long-term memory.
