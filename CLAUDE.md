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
- Initializes a single assistant instance
- Uses `ContextFactory` to create a `Context` object for that agent
- Reads configuration from agent's `config/settings.json`
- Manages lifecycle (starts channels, initializes tools/skills, cleanup on destroy)
- Called by `MultiBootstrap` for each discovered agent

**MultiBootstrap** (`MultiBootstrap.kt`)
- Main Spring Boot service that initializes the system
- Home directory: `~/kokibot` for dev, `~/.kokibot` for prod (configurable via environment)
- Auto-discovers agents from `~/kokibot/agents/` directory (each subdirectory = one agent)
- Creates a separate `Bootstrap` instance for each agent with isolated workspace
- All agents share the same `AssistantRegistry` for cross-agent communication
- Logs warning if `agents/` directory doesn't exist (no agents will be loaded)
- Manages lifecycle with `@PostConstruct` and `@PreDestroy` for all agent bootstraps

**Context** (`Context.kt`)
- Central dependency injection container passed to all components
- Contains: home path, LLM instance, ToolRegistry, SkillRegistry, CommandRegistry, AssistantRegistry, ChatHistory, Memory, SessionLog, DailyLog, SMTP/IMAP config, JsonMapper
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
- Built-in tools: `clock`, `web_search`, `web_fetch`, `python`, `shell`, `mail_*`, `swarm_delegate`
- Tool execution results are added to iteration memory as: "Calling the tool `{name}` returned: {result}"
- Tools are registered in `ToolRegistry` during initialization by `ContextFactory.discoverTools()`
- `swarm_delegate` tool enables multi-agent task delegation (see Multi-Agent System section)

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

Multi-tier persistence and logging system for conversation tracking and knowledge retention:

1. **SessionLog** - Detailed execution trace
   - Location: `~/kokibot/agents/{agent}/memory/sessions/YYYY/MM/DD/{session-id}.jsonl`
   - Format: JSON Lines (one JSON object per line)
   - Captures granular execution details for debugging and analysis:
     - User queries with files and metadata (userId, channelId)
     - LLM responses with reasoning, content, and tool calls
     - Tool use requests with arguments
     - Tool execution results
     - Model information and token usage
     - Iteration memory state
   - Each session entry includes: timestamp, iteration number, role, content array
   - Content types: `text`, `file`, `tool_use`, `tool_result`, `thinking`, `tool`
   - Used for debugging, performance analysis, and cost tracking
   - Organized by date in hierarchical directory structure (YYYY/MM/DD)
   - No automatic cleanup (grows indefinitely for audit purposes)

2. **DailyLog** - Human-readable daily activity journal
   - Location: `~/kokibot/agents/{agent}/memory/history/YYYY-MM-DD.md`
   - Format: Structured Markdown with sections:
     - **🎯 Daily Objectives**: Task checklist for the day
     - **📝 Activity Stream**: Timestamped entries with Intent/Action/Result
     - **💡 Knowledge Capture**: Insights and configuration changes
     - **🚧 Blockers & Next Steps**: Current issues and immediate tasks
   - Created automatically on first interaction of a new day
   - Updated after significant milestones or task switches
   - Carries over context from previous day's "Next Steps" and "Unresolved Blockers"
   - Loaded into system prompt under "# Daily Log Protocol"
   - Provides short-term memory within a 24-hour window
   - Helps maintain context across sessions within the same day

3. **ChatHistory** - Conversation message storage
   - Location: `~/kokibot/agents/{agent}/workspace/history/history.json`
   - Format: JSON array of message objects
   - Stores messages with roles (user/assistant/system) and timestamps
   - Loaded into prompts under "# Conversation History"
   - Provides context for multi-turn conversations
   - Commands: `/clear` to reset
   - Used for maintaining conversation continuity

4. **Memory** - Long-term fact extraction and compaction
   - Location: `~/kokibot/agents/{agent}/workspace/memory/MEMORY.md`
   - Format: Markdown document with key facts and insights
   - Automatic compaction scheduled at configured intervals (e.g., every 6 hours)
   - Compaction process:
     - Extracts messages from ChatHistory within time window (e.g., last 3 days)
     - Uses LLM to identify key facts, decisions, and learnings
     - Merges with existing memory, removing duplicates and outdated info
     - Promotes important knowledge from DailyLog
   - Configuration:
     - `memory.window`: Time window for extraction (e.g., "3d" = 3 days)
     - `memory.compaction-frequency`: How often to compact (e.g., "6h" = 6 hours)
   - Loaded into prompts under "# Long-Term Memory"
   - Commands: `/compact` to manually trigger compaction
   - Prevents context window bloat by distilling conversations into facts

**Memory Hierarchy:**
```
SessionLog (detailed trace)
    ↓ captures execution
DailyLog (daily journal)
    ↓ feeds into
ChatHistory (conversation messages)
    ↓ compacted by LLM
Memory (long-term facts)
```

**Use Cases:**
- **SessionLog**: Debug tool calls, analyze token usage, audit execution paths
- **DailyLog**: Track daily progress, maintain context within a day, capture insights
- **ChatHistory**: Multi-turn conversations, immediate context
- **Memory**: Long-term knowledge retention, prevent context window overflow

**Commands System** (`command/`)
- Special directives invoked with `/command` syntax
- Built-in: `/help`, `/clear`, `/compact`, `/skill [name]`, `/tool [name]`, `/health`
- Implement `Command` interface: `metadata()`, `exec(input, context)`
- Registered in `CommandRegistry` by `ContextFactory.discoverCommands()`

**Multi-Agent System** (`swarm/`)

Kokibot supports multiple specialized assistants working together in a coordinator-specialist pattern:

1. **Architecture**
   - **Coordinator Agent**: Analyzes complex tasks, breaks them into subtasks, delegates to specialists
   - **Specialist Agents**: Focus on specific domains with specialized tools and skills
   - **SwarmDelegateTool**: Enables task delegation between agents
   - **AssistantRegistry**: Central registry for agent discovery and communication

2. **How It Works**
   - `MultiBootstrap` discovers agents from `~/kokibot/agents/` directory at startup
   - Each subdirectory in `agents/` becomes a separate assistant with isolated workspace
   - Agents self-register in `AssistantRegistry` during initialization
   - Coordinator agents use `swarm_delegate` tool to delegate tasks to specialists
   - Delegated tasks return results back to the coordinator for synthesis

3. **Configuration**

   **Directory Structure:**
   ```
   ~/kokibot/agents/
   ├── coordinator/                    # Coordinator agent
   │   ├── config/settings.json        # coordinator: true
   │   ├── ASSISTANT.md                # System instructions
   │   ├── SECURITY.md                 # Security guidelines
   │   ├── workspace/
   │   │   ├── history/
   │   │   └── memory/
   │   └── skills/                     # Coordinator-specific skills
   ├── weather-specialist/             # Weather specialist
   │   ├── config/settings.json
   │   ├── ASSISTANT.md
   │   ├── workspace/
   │   └── skills/
   │       └── weather/
   │           ├── SKILL.md
   │           └── scripts/
   └── crm-specialist/                 # CRM specialist
       ├── config/settings.json
       ├── ASSISTANT.md
       ├── workspace/
       └── skills/
           └── crm/
               └── SKILL.md
   ```

   **Coordinator Settings Example** (`agents/coordinator/config/settings.json`):
   ```json
   {
     "assistant": {
       "coordinator": true,              # Enables coordinator mode
       "max-iterations": 10,
       "description": "Main coordinator for task delegation"
     },
     "llm": {
       "type": "deepseek",
       "api-key": "${DEEPSEEK_API_KEY}",
       "model": "deepseek-chat"
     },
     "channels": [                       # Coordinator typically has channels
       {
         "type": "telegram",
         "token": "${TELEGRAM_TOKEN}"
       }
     ]
   }
   ```

   **Specialist Settings Example** (`agents/weather-specialist/config/settings.json`):
   ```json
   {
     "assistant": {
       "max-iterations": 5,
       "description": "Weather forecasting and climate data specialist"
     },
     "llm": {
       "type": "deepseek",
       "api-key": "${DEEPSEEK_API_KEY}",
       "model": "deepseek-chat"
     },
     "channels": []                      # Specialists typically have no channels
   }
   ```

4. **Coordinator Instructions**
   - When `coordinator: true` is set, the agent receives additional instructions from `/instructions/COORDINATOR.md`
   - Instructions guide task analysis, specialist selection, delegation strategy, and result synthesis
   - Coordinator learns about delegation via the `swarm_delegate` tool metadata

5. **SwarmDelegateTool**
   - **Tool ID**: `swarm_delegate`
   - **Parameters**:
     - `name` (required): Name of the specialist agent (must match directory name)
     - `task` (required): Clear, specific task description
     - `context` (optional): Additional context or constraints
   - **Returns**: Result from specialist prefixed with "Result from `{name}`:"
   - **Error Handling**: Returns error message if agent not found or execution fails

6. **Agent Registry**
   - `AssistantRegistry`: Spring service managing all assistant instances
   - `register(assistant)`: Adds agent to registry (throws `AssistantAlreadyRegisteredException` on duplicate)
   - `get(name)`: Retrieves agent by name (case-insensitive, throws `AssistantNotFoundException` if not found)
   - `all()`: Returns list of all registered agents

7. **Delegation Flow**
   ```
   User → Coordinator Agent
            ↓
   1. Analyzes task
   2. Calls swarm_delegate(name="weather-specialist", task="Get forecast for Paris")
            ↓
   Weather Specialist
     - Processes task with specialized tools/skills
     - Returns result
            ↓
   Coordinator Agent
     - Receives result
     - Synthesizes final response
            ↓
   User ← Final Response
   ```

8. **Best Practices**
   - **Naming**: Use descriptive agent directory names (they become agent identifiers)
   - **Isolation**: Each agent has its own workspace, memory, and skill set
   - **Coordinator Design**: One coordinator with multiple specialists is typical
   - **Channels**: Only coordinator should have channels; specialists are internal-only
   - **Delegation**: Keep delegated tasks focused and specific
   - **Error Handling**: Coordinator should handle specialist failures gracefully

9. **Limitations & Safety**
   - **No Recursion Protection**: Agents can potentially delegate in circles (A→B→A)
   - **No Depth Limits**: Deep delegation chains are possible
   - **No Concurrency Control**: Coordinator can spawn many parallel delegations
   - **Agent Discovery**: Coordinators don't automatically receive list of available specialists
   - **Audit Trail**: Delegated messages tagged with `userId="tool:swarm_delegate"` and `channelId="internal"`

10. **Use Cases**
    - **Domain Separation**: Weather, CRM, finance specialists with different skill sets
    - **Model Specialization**: Fast model for coordination, powerful model for complex reasoning
    - **Access Control**: Specialists with restricted tool access for sensitive operations
    - **Parallel Processing**: Coordinator delegates independent subtasks simultaneously

### Directory Structure

**Agent Setup:**
```
~/kokibot/agents/
├── {agent-name-1}/            # Each subdirectory = one agent
│   ├── config/
│   │   ├── settings.json      # Agent configuration (LLM, channels, memory)
│   │   └── tools/             # Per-tool configuration (optional)
│   │       └── {tool-name}.json
│   ├── ASSISTANT.md           # System instructions (optional)
│   ├── SECURITY.md            # Security guidelines (optional)
│   ├── HEARTBEAT.md           # Heartbeat instructions (optional)
│   ├── skills/                # Agent-specific skills
│   │   └── {skill-name}/
│   │       ├── SKILL.md
│   │       └── scripts/
│   ├── memory/
│   │   ├── sessions/          # SessionLog (YYYY/MM/DD/{session-id}.jsonl)
│   │   └── history/           # DailyLog (YYYY-MM-DD.md)
│   └── workspace/
│       ├── history/
│       │   └── history.json   # ChatHistory - conversation messages
│       ├── memory/
│       │   └── MEMORY.md      # Long-term memory compaction
│       └── files/             # Working files
└── {agent-name-2}/            # Additional agents (optional)
    ├── config/settings.json
    ├── ASSISTANT.md
    ├── skills/
    └── workspace/
```

**Notes:**
- The system requires `~/kokibot/agents/` directory (logs warning if missing)
- For single-agent setup, create one subdirectory (e.g., `~/kokibot/agents/assistant/`)
- For multi-agent setup, create multiple subdirectories (coordinator + specialists)
- Each agent has isolated configuration, skills, and workspace
- Agent directory names become their identifiers (case-insensitive)

### Configuration

Environment variables are substituted via `${VAR_NAME}` syntax in agent settings files (`~/kokibot/agents/{agent-name}/config/settings.json`):

**Example Agent Configuration:**
```json
{
  "assistant": {
    "coordinator": false,
    "max-iterations": 10,
    "max-duration": "5m",
    "thread-pool-size": 4,
    "description": "General purpose assistant"
  },
  "llm": {
    "type": "deepseek",
    "api-key": "${DEEPSEEK_API_KEY}",
    "model": "deepseek-chat",
    "temperature": 0.7,
    "max-tokens": 2048
  },
  "channels": [
    {
      "type": "telegram",
      "token": "${TELEGRAM_TOKEN}"
    }
  ],
  "memory": {
    "window": "3d",
    "compaction-frequency": "6h"
  },
  "heartbeat": {
    "frequency": "30m"
  }
}
```

**Configuration Options:**
- `assistant.coordinator` (boolean): Enable coordinator mode with swarm delegation capabilities
- `assistant.max-iterations` (integer): Max reasoning loop iterations (default: 10)
- `assistant.max-duration` (string): Max processing time per request (e.g., "5m", default: 5 minutes)
- `assistant.thread-pool-size` (integer): Thread pool size (default: 4, min: 2)
- `assistant.description` (string): Agent description (for documentation)
- `memory.window` (string): History window for memory compaction (e.g., "3d" = 3 days)
- `memory.compaction-frequency` (string): How often to compact memory (e.g., "6h" = 6 hours)
- `heartbeat.frequency` (string): Health check frequency (e.g., "30m" = 30 minutes)

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
- `AssistantNotFoundException` - Requested assistant not found in registry (multi-agent)
- `AssistantAlreadyRegisteredException` - Duplicate assistant name during registration (multi-agent)
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
- `AssistantRegistry` - Manages multiple assistant instances (multi-agent mode)

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
