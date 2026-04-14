# Commands Reference

Commands are system-level directives invoked with the `/command` syntax. They provide direct access to Kokibot's internal functionality.

## Table of Contents
- [Overview](#overview)
- [Built-in Commands](#built-in-commands)
- [Usage Examples](#usage-examples)
- [Creating Custom Commands](#creating-custom-commands)

---

## Overview

### What are Commands?

Commands are special system directives that:
- Start with the `/` prefix
- Execute immediately without LLM processing
- Provide system-level functionality
- Return direct responses

### Command Syntax

```
/command [arguments]
```

**Examples:**
- `/help` - No arguments
- `/tool clock` - With argument
- `/skill my-skill` - With argument

---

## Built-in Commands

### `/help`

Display available commands or get details about a specific command.

**Syntax:**
```
/help [command-name]
```

**Examples:**

List all commands:
```
/help
```

Get details about a specific command:
```
/help clear
```

**Response:**
```
Available Commands:
- /help [cmd] - Display available commands or command details
- /clear - Clear conversation history
- /compact - Manually trigger memory compaction
- /health - System health check
- /skill [name] - Show skill details or list all skills
- /tool [name] - Show tool details or list all tools
```

---

### `/clear`

Clear the conversation history from ChatHistory.

**Syntax:**
```
/clear
```

**Purpose:**
- Remove all messages from chat history
- Start a fresh conversation
- Reduce prompt size for long conversations

**Effect:**
- Deletes `~/kokibot/workspace/history/history.json`
- Long-term memory (MEMORY.md) is preserved
- Current session context is reset

**Example:**
```
User: /clear
Bot: Conversation history cleared successfully.
```

⚠️ **Warning:** This action cannot be undone. All conversation history will be permanently deleted.

💡 **Tip:** Use this when:
- Starting a new topic
- Conversation context becomes confusing
- Prompt size approaches token limits

---

### `/compact`

Manually trigger memory compaction.

**Syntax:**
```
/compact
```

**Purpose:**
- Extract facts from recent chat history
- Update long-term memory (MEMORY.md)
- Reduce reliance on full chat history

**Process:**
1. Extract last N days of chat history (configured via `memory.window`)
2. Send to LLM with compaction prompt
3. LLM extracts key facts and information
4. Merge with existing MEMORY.md
5. Save updated memory

**Configuration:**
```json
{
  "memory": {
    "window": 3,
    "compaction-frequency": 6
  }
}
```

- `window`: Days of history to compact (default: 3)
- `compaction-frequency`: Auto-compaction interval in hours (default: 6)

**Example:**
```
User: /compact
Bot: Memory compaction completed. Extracted 15 facts from last 3 days.
```

💡 **Tip:** Use this after important conversations to ensure facts are preserved.

---

### `/health`

Perform a system health check for all components.

**Syntax:**
```
/health
```

**Checks:**
- LLM provider connectivity
- Tool availability and health
- Channel status
- Memory system status
- Configuration validity

**Response Format:**
```json
{
  "status": "healthy",
  "components": {
    "llm": {
      "status": "ok",
      "provider": "deepseek",
      "model": "deepseek-chat"
    },
    "tools": {
      "status": "ok",
      "count": 10,
      "available": ["clock", "web_search", ...]
    },
    "channels": {
      "status": "ok",
      "active": ["telegram"]
    },
    "memory": {
      "status": "ok",
      "last_compaction": "2026-04-14T08:30:00Z"
    }
  }
}
```

**Status Values:**
- `ok` - Component functioning normally
- `warning` - Component operational but with issues
- `error` - Component unavailable or failed

**Example:**
```
User: /health
Bot: ✅ System Health: All components operational
```

💡 **Tip:** Run `/health` after configuration changes to verify setup.

---

### `/skill`

Show details about skills or list all available skills.

**Syntax:**
```
/skill [skill-name]
```

**Without Arguments - List All Skills:**
```
User: /skill
Bot: Available Skills:
- example-skill: Example skill description
- another-skill: Another skill description
```

**With Argument - Show Skill Details:**
```
User: /skill example-skill
Bot:
# example-skill

Description of the skill

## Tools
- tool_name: Tool description

## Keywords
keyword1, keyword2

## Requirements
- Binaries: python3, curl
- Environment: API_KEY
```

**Response Includes:**
- Skill name and description
- Available tools
- Activation keywords
- Required binaries and environment variables
- Usage instructions

**Example:**
```
User: /skill land-title-verifier
Bot:
# land-title-verifier

Verify property ownership and title history

## Tools
- get_title_history: Retrieve property title records

## Keywords
property, title, ownership, deed

## Requirements
- Environment: PROPERTY_API_KEY
```

💡 **Tip:** Use `/skill` to discover what capabilities are available without activating them.

---

### `/tool`

Show details about tools or list all available tools.

**Syntax:**
```
/tool [tool-name]
```

**Without Arguments - List All Tools:**
```
User: /tool
Bot: Available Tools:
- clock: Get current date and time
- web_search: Search the web via Brave Search
- web_fetch: Fetch content from URLs
- python: Execute Python code
- shell: Execute shell commands
- mail_list: List emails from mailbox
- mail_read: Read email content
- mail_send: Send email
- mail_find: Search emails
- mail_unsubscribe: Unsubscribe from mailing lists
```

**With Argument - Show Tool Details:**
```
User: /tool web_search
Bot:
# web_search

Search the web via Brave Search API

## Parameters
- query (string, required): Search query
- count (integer, optional): Number of results (default: 5)

## Configuration
API key required: BRAVE_SEARCH_API_KEY

## Example
{
  "query": "Kotlin coroutines tutorial",
  "count": 3
}
```

**Response Includes:**
- Tool name and description
- Parameter specifications (name, type, required/optional)
- Configuration requirements
- Usage examples

**Example:**
```
User: /tool mail_send
Bot:
# mail_send

Send email or reply to existing email

## Parameters
- to (string, required): Recipient email
- subject (string, required): Email subject
- body (string, required): Email body (supports HTML)
- reply_to_id (string, optional): Message ID to reply to

## Configuration
SMTP configuration required in settings.json
```

💡 **Tip:** Use `/tool` to understand tool parameters before using them in conversations.

---

## Usage Examples

### Getting Started

**Check what's available:**
```
/help
/tool
/skill
```

**Verify system is working:**
```
/health
```

### Managing Conversations

**Start fresh conversation:**
```
/clear
```

**Preserve important facts:**
```
/compact
```

### Exploring Capabilities

**Find email tools:**
```
/tool mail_send
/tool mail_find
```

**Check if skill exists:**
```
/skill property-search
```

### Debugging

**Check system status:**
```
/health
```

**Verify tool configuration:**
```
/tool web_search
```

**See what skills are loaded:**
```
/skill
```

---

## Creating Custom Commands

Commands are Kotlin classes implementing the `Command` interface.

### Command Interface

```kotlin
interface Command {
    fun metadata(): CommandMetadata
    fun exec(input: String, context: Context): String
}
```

### Step 1: Implement Command

Create a new Kotlin class in `src/main/kotlin/com/wutsi/kokibot/command/`:

```kotlin
package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

class MyCommand : Command {
    override fun metadata() = CommandMetadata(
        name = "mycommand",
        description = "Description of what this command does"
    )
    
    override fun exec(input: String, context: Context): String {
        // Command logic here
        // input: arguments passed to command
        // context: system context with access to tools, LLM, etc.
        
        return "Command result"
    }
}
```

### Step 2: Register Command

Add to `ContextFactory.discoverCommands()`:

```kotlin
private fun discoverCommands(context: Context): List<Command> {
    return listOf(
        HelpCommand(),
        ClearCommand(),
        CompactCommand(),
        HealthCommand(),
        SkillCommand(),
        ToolCommand(),
        MyCommand(), // Add your command here
    )
}
```

### Step 3: Test Command

```bash
# Build and run
mvn clean install
mvn spring-boot:run

# Use command
/mycommand argument
```

### Example: Status Command

A command that displays system statistics:

```kotlin
package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

class StatusCommand : Command {
    override fun metadata() = CommandMetadata(
        name = "status",
        description = "Display system statistics"
    )
    
    override fun exec(input: String, context: Context): String {
        val toolCount = context.toolRegistry.all().size
        val skillCount = context.skillRegistry.all().size
        val historySize = context.chatHistory.list().size
        
        return """
            System Status:
            - Tools: $toolCount
            - Skills: $skillCount
            - History: $historySize messages
        """.trimIndent()
    }
}
```

Usage:
```
/status
```

Response:
```
System Status:
- Tools: 10
- Skills: 3
- History: 42 messages
```

---

## Command Best Practices

### Do's

✅ **Return user-friendly messages**
```kotlin
return "Memory compaction completed successfully"
```

✅ **Handle errors gracefully**
```kotlin
try {
    // Command logic
} catch (e: Exception) {
    return "Error: ${e.message}"
}
```

✅ **Validate input**
```kotlin
if (input.isEmpty()) {
    return "Error: Skill name required. Usage: /skill <name>"
}
```

✅ **Use context for system access**
```kotlin
val tools = context.toolRegistry.all()
val history = context.chatHistory.list()
```

### Don'ts

❌ **Don't perform long-running operations**
```kotlin
// Avoid blocking operations
Thread.sleep(10000) // NO!
```

❌ **Don't modify system state without user awareness**
```kotlin
// Always confirm destructive actions
context.chatHistory.clear() // Be careful!
```

❌ **Don't return raw exceptions**
```kotlin
return e.toString() // NO - too technical
return "Error: ${e.message}" // YES - user-friendly
```

---

## Command vs Tools vs Skills

### When to Use Each

| Feature | Commands | Tools | Skills |
|---------|----------|-------|--------|
| **Invocation** | `/command` | LLM decides | Auto-activated |
| **Purpose** | System control | External capabilities | Domain-specific |
| **Processing** | Direct execution | Part of reasoning | Enhanced reasoning |
| **Context** | Immediate | Within iteration | Entire conversation |

**Commands:**
- System-level operations
- Debugging and diagnostics
- Configuration changes
- Manual triggers

**Tools:**
- Extend assistant capabilities
- Interact with external systems
- Execute code or commands
- Fetch data from APIs

**Skills:**
- Domain-specific expertise
- Custom tool combinations
- Specialized instructions
- Activated by keywords

---

## Troubleshooting

### Command Not Found

**Problem:**
```
/mycommand
Error: Unknown command: mycommand
```

**Solution:**
1. Verify command is implemented
2. Check registration in `discoverCommands()`
3. Rebuild application: `mvn clean install`
4. Restart application

### Command Error

**Problem:**
```
/tool nonexistent
Error executing command
```

**Solution:**
Add error handling in command:
```kotlin
override fun exec(input: String, context: Context): String {
    return try {
        // Command logic
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

### Command Takes Too Long

**Problem:**
Command execution times out

**Solution:**
- Move long-running operations to background
- Use tools for heavy processing
- Consider asynchronous execution
- Return progress updates

---

## See Also

- [Tools Reference](tools.md) - Available tools and parameters
- [Skills Reference](skills.md) - Creating and using skills
- [Architecture](../ARCHITECTURE.md) - System design and components
- [AGENT.md](../../AGENT.md) - Developer guide

---

[← Back to Documentation](../README.md) | [Tools Reference →](tools.md)
