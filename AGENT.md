# AGENT.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokibot is an AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable architecture for LLM
providers, communication channels (Telegram), and tools that extend the assistant's capabilities. The assistant uses
iterative reasoning with tool calls to answer user queries.

## Development Commands

### Build & Test

```bash
# Full build with tests
mvn clean install

# Run tests only
mvn test

# Run a specific test class
mvn test -Dtest=AssistantTest

# Run a specific test method
mvn test -Dtest=AssistantTest#process

# Run the application
mvn spring-boot:run
```

### Code Quality

```bash
# Lint Kotlin code
mvn antrun:run@ktlint

# Auto-format Kotlin code
mvn antrun:run@ktlint-format

# View JaCoCo coverage report (after running tests)
open target/site/jacoco/index.html
```

### Code Coverage Requirements

- **Line coverage:** 92% minimum (jacoco.threshold.line)
- **Class coverage:** 92% minimum (jacoco.threshold.class)
- Application.java is excluded from coverage requirements

## Architecture

### Core Components

**Assistant** (`Assistant.kt`)

- Main agent that processes user prompts through iterative LLM calls
- Implements a reasoning loop with max 10 iterations (configurable via `assistant.max-iterations`)
- Manages tool execution and memory across iterations
- Loads system instructions from `~/kokibot/AGENT.md` (if exists)
- Activates skills dynamically based on user query using `SkillMatcher`
- Handles commands (messages starting with `/`)
- Maintains conversation context through ChatHistory and long-term Memory

**Bootstrap** (`Bootstrap.kt`)

- Spring service that initializes the entire system
- Uses `ContextFactory` to set up LLM provider, channels, tools, commands, and skills
- Home directory: `~/kokibot` (configurable via `user.home` property)
- Configuration loaded from `~/kokibot/config/settings.json`
- Manages lifecycle with @PostConstruct and @PreDestroy hooks

**Context** (`Context.kt`)

- Context object passed to all components containing system state
- Contains: home directory, LLM instance, ToolRegistry, SkillRegistry, CommandRegistry, ChatHistory, Memory, SMTP, IMAP,
  JsonMapper
- Handles initialization of all subsystems via `init()` method
- Manages lifecycle with `destroy()` method

**ContextFactory** (`ContextFactory.kt`)

- Factory service that creates and configures the Context
- Discovers and registers all built-in tools via `discoverTools()`
- Discovers and registers all built-in commands via `discoverCommands()`
- Creates LLM instance based on configuration

### LLM Integration

- Factory pattern for pluggable LLM providers (`LLMFactory`)
- Currently supported: Deepseek
- All LLMs implement the `LLM` interface with `completion(request)` method
- LLMs are initialized with configuration map and ToolRegistry for function calling

### Tools System

Tools extend the assistant's capabilities (web search, date/time, etc).

**Tool Interface:**

- `init(config, context)` - Initialize with configuration
- `metadata()` - Return tool name, description, parameters
- `exec(arguments)` - Execute the tool with parsed arguments
- `destroy()` - Cleanup resources

**Adding a New Tool:**

1. Create class implementing `Tool` interface in `tools/` package
2. Add to `discoverTools()` list in `ContextFactory.kt`
3. Optionally add config in `~/kokibot/config/tools/{tool-name}.json`
4. Tool is automatically registered and made available to LLM

**Built-in Tools:**

- `clock` (`ClockTool`) - Returns current date and time in human-readable format
- `mail_list` (`MailListTool`) - List emails in mailbox using IMAP
- `mail_read` (`MailReadTool`) - Read email content by message ID
- `mail_send` (`MailSendTool`) - Send email or reply to existing email via SMTP
- `mail_find` (`MailFindTool`) - Search emails by criteria (from, subject, date range)
- `mail_unsubscribe` (`MailUnsubscribeTool`) - Unsubscribe from mailing lists
- `python` (`PythonTool`) - Execute Python code using GraalVM polyglot engine
- `shell` (`ShellTool`) - Execute shell commands with safety restrictions (forbids sudo, rm -rf, etc.)
- `web_search` (`WebSearchTool`) - Search the web via Brave Search API
- `web_fetch` (`WebFetchTool`) - Fetch and extract content from URLs (supports HTML, PDF)

### Channels System

Channels are communication interfaces for the assistant.

- Factory pattern for pluggable channels (`ChannelFactory`)
- Currently supported: Telegram
- All channels extend `Channel(agent)` abstract class
- Channels delegate message processing to the Assistant

**Adding a New Channel:**

1. Create class extending `Channel` in `channel/` package
2. Add to `ChannelFactory.create()` method
3. Add configuration in `~/kokibot/config/settings.json` channels array

### Skills System

Skills are modular extensions that dynamically activate tools based on user intent. Skills are discovered from
`~/kokibot/skills/` directory.

**Skill Structure:**
Each skill is a directory containing a `SKILL.md` file with:

- YAML frontmatter with metadata (name, description, keywords, categories, requirements)
- Tools section defining custom tools with parameters
- Instructions for the assistant on how to use the skill
- Optional examples

**SKILL.md Format:**

```markdown
---
name: skill-name
description: Brief description of the skill
requires:
    bins: [ "java", "mvn" ]  # Required binaries
    env: [ "API_KEY" ]  # Required environment variables
metadata:
    keywords: [ "keyword1", "keyword2" ]
    categories: [ "category1" ]
---

# Skill Name

Detailed description of the skill

## Tools

- `tool_name`: Tool description
    - `param_name`: (string) Parameter description
    - `optional_param`: (int) Optional parameter description

## Instructions

Guidelines for using the skill

## Examples

User/action examples
```

**Skill Activation:**

- Skills are automatically activated when user query matches skill keywords
- Activated skills' tools are added to the LLM's available tools for that request
- Skill metadata and instructions are injected into system instructions

**Implementation:**

- `SkillRegistry` discovers and registers skills from `~/kokibot/skills/*/SKILL.md`
- `SkillParser` parses SKILL.md files into `SkillMetadata`
- `SkillMatcher` matches user queries to skills based on keywords
- `SkillTool` wraps skill-defined tools as executable Tool instances

### Commands System

Commands are special directives invoked with `/command` syntax that provide system-level functionality.

**Built-in Commands:**

- `/help` - Display available commands and their descriptions
- `/clear` - Clear conversation history from ChatHistory
- `/compact` - Manually trigger memory compaction
- `/skill [name]` - Show details about a specific skill or list all skills
- `/tool [name]` - Show details about a specific tool or list all tools

**Command Interface:**

- `metadata()` - Return CommandMetadata with name and description
- `exec(input, context)` - Execute the command with input string

**Adding a New Command:**

1. Create class implementing `Command` interface in `command/` package
2. Add to `discoverCommands()` list in `ContextFactory.kt`
3. Command is automatically registered with `/` prefix

### Configuration

**Main Configuration** (`~/kokibot/config/settings.json`)

```json
{
    "assistant": {
        "max-iterations": 10
    },
    "llm": {
        "type": "deepseek",
        "api-key": "${DEEPSEEK_API_KEY}",
        "model": "deepseek-chat"
    },
    "channels": [
        {
            "type": "telegram",
            "token": "${TELEGRAM_TOKEN}"
        }
    ],
    "memory": {
        "window": 3,
        "compaction-frequency": 6
    },
    "mail": {
        "smtp": {
            "host": "smtp.gmail.com",
            "port": 587,
            "username": "${MAIL_USERNAME}",
            "password": "${MAIL_PASSWORD}",
            "from": "your-email@gmail.com"
        },
        "imap": {
            "host": "imap.gmail.com",
            "port": 993,
            "username": "${MAIL_USERNAME}",
            "password": "${MAIL_PASSWORD}"
        }
    }
}
```

**Configuration Details:**

- Environment variables are substituted via `${VAR_NAME}` syntax
- System instructions loaded from `~/kokibot/AGENT.md` (if exists)
- Per-tool configuration in `~/kokibot/config/tools/{tool-name}.json`
- Skills discovered from `~/kokibot/skills/*/SKILL.md`
- `assistant.max-iterations`: Maximum reasoning loop iterations (default: 10)
- `memory.window`: Days of history to compact into memory (default: 3)
- `memory.compaction-frequency`: Hours between automatic memory compaction (default: 6)
- `mail.smtp`: SMTP configuration for sending emails
- `mail.imap`: IMAP configuration for reading emails

### Memory & Conversation

**ChatHistory** (`memory/ChatHistory.kt`)

- Persists conversation history to `~/kokibot/workspace/history/history.json`
- Loaded into each prompt under "# Conversation history"
- Supports `merge(from, to)` to extract conversation snippets by date range
- Can be cleared with `/clear` command

**Memory** (`memory/Memory.kt`)

- Long-term memory system that stores facts and information extracted from conversations
- Automatically compacts chat history into memory on a scheduled basis
- Stored in `~/kokibot/workspace/memory/MEMORY.md`
- Configuration:
    - `window`: Number of days of history to include in compaction (default: 3)
    - `compaction-frequency`: Hours between automatic compaction runs (default: 6)
- Uses LLM to extract and merge facts from conversation history
- Can be manually triggered with `/compact` command
- Loaded into prompts under "# Long-Term Memory"

**Prompt Structure:**

1. User query
2. Previous reasoning steps and tool observations (if any)
3. Full conversation history in JSON format
4. Long-term memory in Markdown format (if exists)

**Iteration Memory:**

- Assistant maintains per-iteration memory for reasoning steps
- Each tool call result is stored: "Calling the tool `{name}` returned: {result}"
- Memory persists across iterations within a single request

### Directory Structure

```
~/kokibot/
├── config/
│   ├── settings.json          # Main configuration file
│   └── tools/                 # Per-tool configuration (optional)
│       └── {tool-name}.json
├── skills/                    # Custom skills directory
│   └── {skill-name}/
│       └── SKILL.md           # Skill definition
├── workspace/
│   ├── history/
│   │   └── history.json       # Conversation history
│   └── memory/
│       └── MEMORY.md          # Long-term memory
└── AGENT.md                   # System instructions (optional)
```

### Key Dependencies

**Runtime:**

- Spring Boot 4.0.5 - Application framework
- Kotlin 2.2.0 - Primary programming language
- Jackson Kotlin Module 2.21.1 - JSON serialization
- GraalVM Polyglot 25.0.2 - Python execution engine
- Jakarta Mail API 2.1.5 - Email functionality
- Telegram Bots 9.5.0 - Telegram integration
- JSoup 1.22.1 - HTML parsing
- Apache PDFBox 3.0.6 - PDF text extraction
- Flexmark 0.64.8 - HTML to Markdown conversion

**Testing:**

- JUnit 5 - Test framework
- Mockito Kotlin 2.2.0 - Mocking library
- GreenMail 2.1.8 - Email testing
- Spring Boot Test - Integration testing

### Implementation Notes

**Error Handling:**

- `TooManyIterationException` thrown when max iterations exceeded
- Tool execution errors are caught and returned as error messages to LLM
- Configuration errors throw `ConfigurationException` during initialization
- Command/Skill/Tool not found throw respective `*NotFoundException`

**Security:**

- ShellTool forbids dangerous commands: `sudo`, `rm -rf`, `chmod`, `chown`, `> /etc/`
- Shell command timeout: 5 seconds (configurable)
- PythonTool executes in sandboxed GraalVM context

**Concurrency:**

- Memory compaction runs on scheduled executor (single thread)
- Telegram channel uses long polling for updates

## Testing

- Framework: JUnit 5
- Mocking: mockito-kotlin (`com.nhaarman.mockitokotlin2`)
- Tests use `mock<Type>()` and `whenever(...).doReturn(...)` pattern
- Test resources in `src/test/resources/` (referenced via `getResourceFile()` helper)
- Spring Boot test utilities available but most tests are pure unit tests

## Code Style

- 4-space indentation (2 for JSON/YAML/XML)
- K&R brace style
- ktlint_official code style with many rules disabled (see .editorconfig)
- No max line length enforcement
- Insert final newline, trim trailing whitespace
