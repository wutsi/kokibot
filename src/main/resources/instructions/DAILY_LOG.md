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
- [Configuration changes made to the local machine]

## 🚧 Blockers & Next Steps

- **Blockers:** What is currently stopping progress?
- **Next Steps:** Immediate tasks for the next session.
```

## Real-Time Updates

- **Frequency:** Update the log after every significant milestone or when switching tasks — not on every message.
  The file is re-read into every prompt as Short-Term Memory, so writing on each turn bloats context for the rest of
  the day for no benefit.
- **Atomic Entries:** Keep the "Activity Stream" concise. Focus on why a change was made rather than just what was
  changed.
- **How to write:** The file already exists after Initialization, so every update is an edit, not a fresh write.
  Read the current file first, then append the new content (new Activity Stream entry, updated Objectives checkmark,
  or new Knowledge Capture bullet) to the relevant section using `file_edit`. If you use `file_write` instead, you
  must pass `overwrite=true` with the full merged content — the file will already exist and the write will otherwise
  fail. Never replace the file's existing content instead of merging into it.
- **Persistence:** Ensure the file is saved locally to maintain a "paper trail" for memory compaction and long-term
  retrieval.

## Memory Integration

- When a task is completed, update the `Daily Objectives` list with a completion mark.
- If a specific insight is gained, explicitly document it in the `Knowledge Capture` section so it can be promoted to
  long-term memory.

## Retrospective (Self-Improvement)

At the end of every significant task — not just at end of day — add a short retrospective entry under
`## 💡 Knowledge Capture`, tagged `Lesson:`, answering only what is non-obvious:

- What approach worked well that isn't already covered by existing instructions or long-term `Memory`?
- What mistake, wrong assumption, or inefficient approach occurred, and what should be done differently next time?

Phrase each lesson as an actionable rule ("Always/Never do X because Y"), not a narrative of what happened — this is
what gets carried forward during memory compaction. Keep it to 1-2 sentences. Do not log routine successes or
anything already stated in `ASSISTANT.md` or `Memory`.

No manual promotion step is needed: `Lesson:` entries are picked up automatically as "Process Lessons" during the
next long-term memory compaction cycle, which merges recurring themes and discards one-off lessons that never
recur or were never confirmed by the user (see `MEMORY.md`).
