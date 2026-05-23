# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokibot is an AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable architecture for LLM
providers (Deepseek, Kimi, Gemini), communication channels (Telegram), and extensible tools/skills that enable complex
reasoning and task execution through iterative LLM calls.

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
4. **LLM** receives prompt with: system instructions + skill instructions + user query + history + memory + available
   tools
5. **LLM** responds with text or tool call requests
6. If tool call: **Assistant** executes tool, adds result to iteration memory, loops back to step 4
7. If text response: **Assistant** persists to history and returns to channel
8. Response sent to user

### Parallel Tool Execution

The assistant executes independent tool calls in parallel to reduce response time:

1. **LLM** returns response with multiple tool calls
2. **Assistant** collects all tool calls from all choices
3. **Thread Pool** submits all tool calls as concurrent tasks
4. **Assistant** blocks until all tool calls complete
5. Results are added to iteration memory in order
6. Loop continues with next LLM call

**Configuration:**
- `assistant.thread-pool-size`: Controls concurrency (default: 4 threads)
- Minimum 2 threads enforced
- Thread pool gracefully shuts down with assistant

**Error Handling:**
- Individual tool failures don't block other tools
- Errors are logged and returned as tool results
- Timeouts and cancellations are handled gracefully

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
- Contains: home path, LLM instance, ToolRegistry, SkillRegistry, CommandRegistry, AssistantRegistry, ChatHistory,
  Memory, SessionLog, DailyLog, SMTP/IMAP config, JsonMapper
- Lifecycle methods: `init()` and `destroy()`

**Assistant** (`Assistant.kt`)

- Main orchestration engine implementing the reasoning loop
- Processes both user queries and commands (starting with `/`)
- Dynamically activates skills using `SkillMatcher` based on query keywords
- Builds prompts from: system instructions (`~/kokibot/AGENT.md`) + activated skill instructions + user query +
  iteration memory + conversation history + long-term memory
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
- Built-in tools: `web_search`, `web_fetch`, `python`, `shell`, `file_read`, `file_write`, `file_edit`, `skill_activation`, `swarm_delegate`, `send_message`
- Tool execution results are added to iteration memory as: "Calling the tool `{name}` returned: {result}"
- Tools are registered in `ToolRegistry` during initialization by `ContextFactory.discoverTools()`
- `swarm_delegate` tool enables multi-agent task delegation (see Multi-Agent System section)
- File tools (`file_read`, `file_write`, `file_edit`) allow agents to manipulate files in their workspace

**Adding a New Tool:**

1. Create class implementing `Tool` interface in `tools/` package
2. Add to `discoverTools()` list in `ContextFactory.kt`
3. Optionally add config in `~/kokibot/config/tools/{tool-name}.json`
4. Tool metadata is automatically exposed to LLM for function calling

**Skills System** (`skill/`)

- Modular extensions activated on demand by the LLM via the `skill_activation` tool
- Discovered at startup from `{agent-home}/skills/*/SKILL.md`
  ([`SkillRegistry.initSkills`](src/main/kotlin/com/wutsi/kokibot/skill/SkillRegistry.kt))
- Additional skills can be sourced from Git-hosted **marketplaces** (see "Marketplace" below) and registered alongside
  local skills
- Each skill defines: name, description, keywords, categories, required binaries/env vars, custom tools, instructions,
  examples
- `SkillTool` wraps skill-defined tools as executable `Tool` instances
- Skills may include Python scripts in a `scripts/` subdirectory

**Marketplace** (`marketplace/`)

- A marketplace is a Git repository containing reusable skills
- [`MarketplaceRegistry`](src/main/kotlin/com/wutsi/kokibot/marketplace/) loads marketplace definitions from the agent
  config and uses [`GitSkillFinder`](src/main/kotlin/com/wutsi/kokibot/marketplace/) to clone/update them
- Skills found in marketplaces are registered into the agent's `SkillRegistry`
- Exception: `MarketplaceNotFoundException`

**Channels System** (`channel/`)

- Communication interfaces that connect users to the assistant
- Factory pattern: `ChannelFactory.create(type)` returns a `Channel`
  ([`ChannelRegistry`](src/main/kotlin/com/wutsi/kokibot/channel/ChannelRegistry.kt) wires it to the assistant)
- All channels extend the `Channel` abstract class
- Implementations:
    - `TelegramChannel` ([channel/telegram/](src/main/kotlin/com/wutsi/kokibot/channel/telegram/)) — long polling with
      Markdown→HTML conversion
    - `EmailChannel` ([channel/email/](src/main/kotlin/com/wutsi/kokibot/channel/email/)) — IMAP inbox polling + SMTP
      replies
    - `WebSocketChannel` ([channel/websocket/](src/main/kotlin/com/wutsi/kokibot/channel/websocket/)) — real-time
      bidirectional communication
- Exception: `ChannelNotFoundException`

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

Environment variables are substituted via `${VAR_NAME}` syntax in agent settings files (
`~/kokibot/agents/{agent-name}/config/settings.json`):

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
- **Jackson Kotlin 2.21.3** - JSON serialization
- **Telegram Bots 9.6.0** - Telegram integration
- **JSoup 1.22.2** - HTML parsing
- **Apache PDFBox 3.0.7** - PDF text extraction
- **Apache POI 5.5.1** - Office document parsing
- **Flexmark 0.64.8** - HTML to Markdown conversion
- **JGit 7.6.0** - Git operations
- **OkHttp 5.3.2** - HTTP client
- **Kotlinx Coroutines 1.11.0** - Async programming
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

- Executes Python code via subprocess
- Isolated execution environment
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
2. **New Skills** - Add `SKILL.md` to `~/kokibot/agents/{agent}/skills/` (auto-discovered), or publish in a marketplace
3. **New Channels** - Extend `Channel` abstract class, register in `ChannelFactory.create()`
4. **New LLM Providers** - Implement `LLM` interface, register in `LLMFactory.create()`
5. **New Commands** - Implement `Command` interface, register in `ContextFactory.discoverCommands()`

# Coding Guidelines

Behavioral guidelines to reduce common LLM coding mistakes, derived
from [Andrej Karpathy's observations](https://x.com/karpathy/status/2015883857489522876) on LLM coding pitfalls.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
