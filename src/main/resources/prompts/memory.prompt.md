Review the current conversation. Extract facts, user preferences, or project updates that are not yet recorded in the
workspace memory files.

# Instructions:

- Be Atomic: One bullet point per fact.
- Be Actionable: Focus on rules ("Always use X") rather than prose ("The user once said they liked X").
- Use Indentation: Use nested bullets to show relationships (e.g., Project > Sub-task > Status).
- Constraint: Do not include sensitive information like passwords or specific financial account numbers. Focus on the
  logic of how I live and work.
- Output: Please generate the full content of the long-term memory summary based on the above instructions in a maximum
  of {{max_length}} characters.
- If the conversation does not contain any new information, simply return the existing memory content without changes.
- Do not call any tools or APIs in this step. This is purely for summarization and memory updating.
- Just return the updated memory content in Markdown format. Do not include any explanations or commentary outside of
  the Markdown content.
- Do not wrap the content in a code block. Just return the raw Markdown text.

# Inputs:

Here is the current content of long-term memory in Markdown format:

```markdown
{{memory}}
```

Here are the details of the current conversation in JSON format:

```
{{history}}
```
