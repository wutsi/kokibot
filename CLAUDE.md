# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokibot is an AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable architecture for LLM providers (Deepseek, Kimi, Gemini), communication channels (Telegram), and extensible tools/skills that enable complex reasoning and task execution through iterative LLM calls.

## Build & Development Commands

### Linting
```bash
# IMPORTANT: Always run ktlint format before building or committing
mvn antrun:run@ktlint-format

# Check for linting issues (runs automatically during build)
mvn antrun:run@ktlint
```

### Building
```bash
# Full build with tests (includes ktlint validation)
mvn clean install

# Run application locally
mvn spring-boot:run
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AssistantTest

# Run specific test method
mvn test -Dtest=AssistantTest#process

# View coverage report (after running tests)
open target/site/jacoco/index.html
```

### Code Coverage Requirements
- **Line coverage:** 90% minimum (jacoco.threshold.line)
- **Class coverage:** 90% minimum (jacoco.threshold.class)
- Application.kt is excluded from coverage

## Architecture

### Core Request Flow

The assistant uses an **iterative reasoning loop** (max 10 iterations, configurable via `assistant.max-iterations`):

1. **Channel** (e.g., Telegram) receives user message
2. **Assistant** loads conversation history and long-term memory
3. **Assistant** activates relevant skills based on keywords in user query
4. **LLM** receives prompt with: system instructions + skill instructions + user query + history + memory + available tools
5. **LLM** responds with text or tool call requests
6. If tool call: **Assistant** executes tool, adds result to iteration memory, loops back to step 4
7. If text response: **Assistant** persists to history and returns to channel
8. Response sent to user

### Key Components

**Bootstrap** (`Bootstrap.kt`)
- Spring Boot service that initializes the entire system
- Uses `ContextFactory` to create the global `Context` object
- Home directory: `~/kokibot` (or configurable via `user.home` property)
- Configuration: `~/kokibot/config/settings.json`
- Manages lifecycle with `@PostConstruct` (starts channels) and `@PreDestroy` (cleanup)

**Context** (`Context.kt`)
- Central dependency injection container passed to all components
- Contains: home path, LLM instance, ToolRegistry, SkillRegistry, CommandRegistry, ChatHistory, Memory, SMTP/IMAP config, JsonMapper
- Lifecycle methods: `init()` and `destroy()`

**Assistant** (`Assistant.kt`)
- Main orchestration engine implementing the reasoning loop
- Processes both user queries and commands (starting with `/`)
- Dynamically activates skills using `SkillMatcher` based on query keywords
- Builds prompts from: system instructions (`~/kokibot/AGENT.md`) + activated skill instructions + user query + iteration memory + conversation history + long-term memory
- Coordinates tool execution and manages iteration memory within a single request

### Subsystems

**LLM Integration** (`llm/`)
- Factory pattern: `LLMFactory.create(type, config)` → LLM instance
- Supported providers: Deepseek, Kimi, Gemini (all implement `LLM` interface)
- All LLMs initialized with config map and ToolRegistry for function calling
- `LLM.completion(request)` returns `LLMResponse` with text or tool calls

**Tools System** (`tools/`)
- Tools extend assistant capabilities with external integrations
- Interface: `init(config, context)`, `metadata()`, `exec(arguments)`, `destroy()`
- Built-in tools: `clock`, `web_search`, `web_fetch`, `python`, `shell`, `mail_*`
- Tool execution results are added to iteration memory as: "Calling the tool `{name}` returned: {result}"
- Tools are registered in `ToolRegistry` during initialization by `ContextFactory.discoverTools()`

**Adding a New Tool:**
1. Create class implementing `Tool` interface in `tools/` package
2. Add to `discoverTools()` list in `ContextFactory.kt`
3. Optionally add config in `~/kokibot/config/tools/{tool-name}.json`
4. Tool metadata is automatically exposed to LLM for function calling

**Skills System** (`skills/`)
- Modular extensions that dynamically activate based on user intent
- Skills discovered from `~/kokibot/skills/*/SKILL.md` at startup
- Each skill defines: name, description, keywords, categories, required binaries/env vars, custom tools, instructions, examples
- `SkillMatcher` activates skills when user query matches keywords
- Activated skills inject their tools and instructions into the LLM prompt for that request only
- `SkillTool` wraps skill-defined tools as executable `Tool` instances
- Skills can include Python scripts in `scripts/` subdirectory

**Channels System** (`channel/`)
- Communication interfaces that connect users to the assistant
- Factory pattern: `ChannelFactory.create(type, assistant)` → Channel instance
- All channels extend `Channel(agent)` abstract class
- Channels implement message receiving and delegate processing to Assistant
- Current implementation: `TelegramChannel` (long polling with HTML formatting)

**Memory & History** (`memory/`)

Two-tier persistence system:

1. **ChatHistory** - Short-term conversation storage
   - File: `~/kokibot/workspace/history/history.json`
   - Stores messages with roles (user/assistant) and timestamps
   - Loaded into every prompt under "# Conversation history"
   - Commands: `/clear` to reset

2. **Memory** - Long-term fact extraction
   - File: `~/kokibot/workspace/memory/MEMORY.md`
   - Automatic compaction every 6 hours (configurable via `memory.compaction-frequency`)
   - Extracts last 3 days of history (configurable via `memory.window`)
   - Uses LLM to extract key facts and merge with existing memory
   - Loaded into prompts under "# Long-Term Memory"
   - Commands: `/compact` to manually trigger

**Commands System** (`command/`)
- Special directives invoked with `/command` syntax
- Built-in: `/help`, `/clear`, `/compact`, `/skill [name]`, `/tool [name]`, `/health`
- Implement `Command` interface: `metadata()`, `exec(input, context)`
- Registered in `CommandRegistry` by `ContextFactory.discoverCommands()`

### Directory Structure

```
~/kokibot/
├── config/
│   ├── settings.json          # Main configuration (LLM, channels, memory)
│   └── tools/                 # Per-tool configuration (optional)
│       └── {tool-name}.json
├── skills/                    # Custom skills
│   └── {skill-name}/
│       ├── SKILL.md           # Skill definition
│       └── scripts/           # Python/shell scripts (optional)
├── workspace/
│   ├── history/
│   │   └── history.json       # Conversation history
│   └── memory/
│       └── MEMORY.md          # Long-term memory
└── AGENT.md                   # System instructions (optional)
```

### Configuration

Environment variables are substituted via `${VAR_NAME}` syntax in `~/kokibot/config/settings.json`:

```json
{
  "assistant": {
    "max-iterations": 10
  },
  "llm": {
    "type": "deepseek",
    "api-key": "${KOKIBOT_LLM_API_KEY}",
    "model": "deepseek-chat"
  },
  "channels": [
    {
      "type": "telegram",
      "token": "${KOKIBOT_TOKEN}"
    }
  ],
  "memory": {
    "window": 3,
    "compaction-frequency": 6
  }
}
```

## Technology Stack

- **Kotlin 2.3.21** - Primary language
- **Java 17** - Runtime
- **Spring Boot 4.0.6** - Application framework
- **Jackson Kotlin 2.21.2** - JSON serialization
- **GraalVM Polyglot 25.0.2** - Python execution engine
- **Telegram Bots 9.5.0** - Telegram integration
- **JSoup 1.22.2** - HTML parsing
- **Apache PDFBox 3.0.7** - PDF text extraction
- **Apache POI 5.5.1** - Office document parsing
- **Flexmark 0.64.8** - HTML to Markdown conversion
- **JUnit 5** - Testing framework
- **Mockito Kotlin 2.2.0** - Mocking library

## Code Style

- **Indentation:** 4 spaces for Kotlin (2 for JSON/YAML/XML)
- **Brace style:** K&R
- **Linter:** ktlint with ktlint_official style (many rules disabled in .editorconfig)
- **No max line length enforcement**
- Insert final newline, trim trailing whitespace
- **CRITICAL:** Always run `mvn antrun:run@ktlint-format` before committing

## Testing

- Framework: JUnit 5
- Mocking: `com.nhaarman.mockitokotlin2.mockito-kotlin`
- Use `mock<Type>()` and `whenever(...).doReturn(...)` pattern
- Test resources: `src/test/resources/` (access via helper methods)
- Most tests are pure unit tests (Spring Boot test utilities available for integration tests)

## Error Handling

- `TooManyIterationException` - Max reasoning iterations exceeded
- `ConfigurationException` - Invalid configuration during initialization
- `ToolNotFoundException` - Requested tool not found in registry
- `SkillNotFoundException` - Requested skill not found in registry
- `CommandNotFoundException` - Requested command not found in registry
- Tool execution errors are caught and returned as error messages to LLM

## Security

**ShellTool** (`tools/ShellTool.kt`)
- Blacklist: `sudo`, `rm -rf`, `chmod`, `chown`, `> /etc/`
- Timeout: 5 seconds (configurable)
- No pipe redirection to system directories

**PythonTool** (`tools/PythonTool.kt`)
- Executes in sandboxed GraalVM context
- No file system access outside sandbox
- Limited execution time

**Configuration**
- Sensitive data stored in environment variables
- No credentials in code or config files
- Environment variable substitution: `${VAR_NAME}`

## Implementation Patterns

### Factory Pattern
- `LLMFactory` - Creates LLM provider instances
- `ChannelFactory` - Creates communication channel instances
- `ContextFactory` - Creates and configures the global Context

### Registry Pattern
- `ToolRegistry` - Manages available tools
- `SkillRegistry` - Manages discovered skills
- `CommandRegistry` - Manages system commands

### Strategy Pattern
- `Tool` interface - Different tool implementations
- `LLM` interface - Different LLM providers
- `Channel` abstract class - Different communication channels

## Extension Points

1. **New Tools** - Implement `Tool` interface, register in `ContextFactory.discoverTools()`
2. **New Skills** - Add `SKILL.md` to `~/kokibot/skills/` (auto-discovered)
3. **New Channels** - Extend `Channel` abstract class, register in `ChannelFactory.create()`
4. **New LLM Providers** - Implement `LLM` interface, register in `LLMFactory.create()`
5. **New Commands** - Implement `Command` interface, register in `ContextFactory.discoverCommands()`
