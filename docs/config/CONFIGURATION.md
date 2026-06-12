# Configuration Guide

This document provides a comprehensive guide to configuring Kokibot. All configuration is done through JSON files
located in each agent's `config/` directory.

## Table of Contents

- [Configuration File Location](#configuration-file-location)
- [Environment Variable Substitution](#environment-variable-substitution)
- [Complete Configuration Example](#complete-configuration-example)
- [Configuration Sections](#configuration-sections)
    - [Assistant Configuration](#assistant-configuration)
    - [LLM Configuration](#llm-configuration)
    - [Channel Configuration](#channel-configuration)
    - [Memory Configuration](#memory-configuration)
    - [Heartbeat Configuration](#heartbeat-configuration)
    - [Marketplace Configuration](#marketplace-configuration)
    - [Swarm Configuration](#swarm-configuration)
- [Tool-Specific Configuration](#tool-specific-configuration)

---

## Configuration File Location

Each agent has its own configuration file:

```
~/kokibot/agents/
└── {agent-name}/
    ├── config/
    │   ├── settings.json        # Main configuration file
    │   └── tools/               # Optional tool-specific configs
    │       └── {tool-name}.json
    └── ...
```

**Development vs Production:**

- **Development mode** (default): `~/kokibot/`
- **Production mode** (Spring profile `prod`): `~/.kokibot/`

---

## Environment Variable Substitution

Kokibot supports environment variable substitution using the `${VAR_NAME}` syntax. This is useful for storing sensitive
data like API keys outside of configuration files.

**Example:**

```json
{
    "llm": {
        "api-key": "${DEEPSEEK_API_KEY}"
    }
}
```

Set the environment variable before starting Kokibot:

```bash
export DEEPSEEK_API_KEY="your-actual-api-key"
```

---

## Complete Configuration Example

Here's a comprehensive example showing all available configuration options:

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
        "max-tokens": 2048,
        "streaming": false,
        "thinking": false,
        "reasoning-effort": null,
        "read-timeout-millis": 60000,
        "connect-timeout-millis": 5000
    },
    "channels": [
        {
            "type": "telegram",
            "token": "${TELEGRAM_TOKEN}",
            "thread-pool-size": 4,
            "sender-whitelist": []
        },
        {
            "type": "email",
            "email": "bot@example.com",
            "username": "bot@example.com",
            "password": "${EMAIL_PASSWORD}",
            "imap-host": "imap.example.com",
            "imap-port": 993,
            "imap-ssl": true,
            "smtp-host": "smtp.example.com",
            "smtp-port": 465,
            "smtp-ssl": true,
            "fetch-frequency": "15m",
            "sender-whitelist": []
        },
        {
            "type": "websocket",
            "path": "/ws/agent-name"
        }
    ],
    "memory": {
        "window": "3d",
        "compaction-frequency": "6h",
        "max-length": 10240
    },
    "heartbeat": {
        "frequency": "30m"
    },
    "marketplaces": [
        {
            "url": "https://github.com/username/skill-marketplace.git",
            "branch": "main"
        }
    ],
    "swarm": {
        "max-depth": 5,
        "detect-cycles": true
    }
}
```

---

## Configuration Sections

### Assistant Configuration

Controls the core assistant behavior and reasoning loop.

**Section:** `assistant`

| Parameter          | Type    | Default | Description                                                                                                                       |
|--------------------|---------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `coordinator`      | boolean | `false` | Enable coordinator mode for multi-agent delegation. When `true`, loads additional instructions from `COORDINATOR.md`              |
| `max-iterations`   | integer | `10`    | Maximum number of reasoning loop iterations per request. Prevents infinite loops                                                  |
| `max-duration`     | string  | `"5m"`  | Maximum processing time per request. Format: `{number}{unit}` where unit is `s` (seconds), `m` (minutes), `h` (hours), `d` (days) |
| `thread-pool-size` | integer | `4`     | Number of threads for parallel tool execution. Minimum: 2                                                                         |
| `description`      | string  | `""`    | Human-readable description of the assistant's purpose                                                                             |

**Example:**

```json
{
    "assistant": {
        "coordinator": true,
        "max-iterations": 15,
        "max-duration": "10m",
        "thread-pool-size": 8,
        "description": "Coordinator agent for task delegation"
    }
}
```

**Context Length Calculation:**

The assistant tracks context length (in tokens) for display in WebSocket clients:
- Calculated as: `(prompt length + system instructions length) / 4`
- Assumes approximately 4 bytes per token (common for English text)
- Sent with `FINAL` messages as `contextLength` field
- Used by web interface to display context gauge

**Duration Format Examples:**

- `"30s"` = 30 seconds
- `"5m"` = 5 minutes
- `"2h"` = 2 hours
- `"1d"` = 1 day

---

### LLM Configuration

Configures the Large Language Model provider and its parameters.

**Section:** `llm`

#### Common Parameters (All Providers)

| Parameter                | Type    | Required | Description                                             |
|--------------------------|---------|----------|---------------------------------------------------------|
| `type`                   | string  | ✅        | LLM provider type. Values: `deepseek`, `kimi`, `gemini` |
| `api-key`                | string  | ✅        | API key for the LLM provider                            |
| `model`                  | string  | ✅        | Model identifier (provider-specific)                    |
| `temperature`            | number  | ❌        | Sampling temperature (0.0-2.0). Higher = more creative  |
| `max-tokens`             | integer | ❌        | Maximum tokens in response                              |
| `read-timeout-millis`    | integer | ❌        | API read timeout in milliseconds                        |
| `connect-timeout-millis` | integer | ❌        | API connection timeout in milliseconds                  |
| `streaming`              | boolean | `false`  | Enable streaming responses (SSE). Enables real-time token usage display                  |
| `stream-timeout-millis`  | integer | `120000` | Streaming timeout in milliseconds (default: 2 minutes). Prevents indefinite hangs        |
| `thinking`               | boolean | `false`  | Enable extended thinking mode (Deepseek R1)                                              |
| `reasoning-effort`       | string  | `null`   | Reasoning effort level. Values: `low`, `medium`, `high`                                  |

**Deepseek Example:**

```json
{
    "llm": {
        "type": "deepseek",
        "api-key": "${DEEPSEEK_API_KEY}",
        "model": "deepseek-chat",
        "temperature": 0.7,
        "max-tokens": 4096,
        "streaming": true,
        "stream-timeout-millis": 120000,
        "thinking": false,
        "read-timeout-millis": 60000,
        "connect-timeout-millis": 5000
    }
}
```

**Streaming Configuration:**

When `streaming: true`:
- Responses stream in real-time via Server-Sent Events (SSE)
- Token usage data included with each chunk
- WebSocket clients display accumulated token usage with K-suffix formatting
- Set `stream-timeout-millis` to prevent indefinite hangs (default: 2 minutes)

**Token Usage Display (WebSocket):**
- Accumulates across all LLM calls in a message
- Formats numbers ≥1000 with K suffix (e.g., 1.5K, 10.1K)
- Shows breakdown: prompt, completion, cached tokens
- Example: `3.8K tokens (2.5K prompt, 1.3K completion) 💾 1.2K cached`

**Kimi Example:**

```json
{
    "llm": {
        "type": "kimi",
        "api-key": "${KIMI_API_KEY}",
        "model": "moonshot-v1-8k",
        "temperature": 0.8,
        "max-tokens": 2048
    }
}
```

**Gemini Example:**

```json
{
    "llm": {
        "type": "gemini",
        "api-key": "${GEMINI_API_KEY}",
        "model": "gemini-1.5-pro",
        "temperature": 0.5,
        "max-tokens": 2048
    }
}
```

**Available Models:**

| Provider     | Models                                                    |
|--------------|-----------------------------------------------------------|
| **Deepseek** | `deepseek-chat`, `deepseek-reasoner`, `deepseek-v4-flash` |
| **Kimi**     | `moonshot-v1-8k`, `moonshot-v1-32k`, `moonshot-v1-128k`   |
| **Gemini**   | `gemini-1.5-pro`, `gemini-1.5-flash`, `gemini-2.0-flash`  |

---

### Channel Configuration

Configures communication channels for user interaction.

**Section:** `channels` (array)

#### Telegram Channel

Real-time messaging via Telegram Bot API with long polling.

| Parameter          | Type    | Required | Description                                                      |
|--------------------|---------|----------|------------------------------------------------------------------|
| `type`             | string  | ✅        | Must be `"telegram"`                                             |
| `token`            | string  | ✅        | Telegram Bot API token from [@BotFather](https://t.me/botfather) |
| `thread-pool-size` | integer | ❌        | Worker threads for concurrent message processing (default: 4)    |
| `sender-whitelist` | array   | ❌        | List of allowed Telegram usernames (empty = allow all)           |

**Example:**

```json
{
    "channels": [
        {
            "type": "telegram",
            "token": "${TELEGRAM_TOKEN}",
            "thread-pool-size": 8,
            "sender-whitelist": [
                "alice",
                "bob"
            ]
        }
    ]
}
```

**Getting a Telegram Bot Token:**

1. Message [@BotFather](https://t.me/botfather) on Telegram
2. Send `/newbot` command
3. Follow instructions to create your bot
4. Copy the API token

---

#### Email Channel

Email-based communication using IMAP (receiving) and SMTP (sending).

| Parameter          | Type    | Required | Description                                                |
|--------------------|---------|----------|------------------------------------------------------------|
| `type`             | string  | ✅        | Must be `"email"`                                          |
| `email`            | string  | ✅        | Email address for the bot                                  |
| `username`         | string  | ✅        | Email account username (often same as email)               |
| `password`         | string  | ✅        | Email account password or app-specific password            |
| `imap-host`        | string  | ✅        | IMAP server hostname                                       |
| `imap-port`        | integer | ❌        | IMAP server port (default: 993)                            |
| `imap-ssl`         | boolean | ❌        | Enable SSL for IMAP (default: true)                        |
| `imap-tls`         | boolean | ❌        | Enable TLS for IMAP (default: false)                       |
| `smtp-host`        | string  | ✅        | SMTP server hostname                                       |
| `smtp-port`        | integer | ❌        | SMTP server port (default: 465)                            |
| `smtp-ssl`         | boolean | ❌        | Enable SSL for SMTP (default: true)                        |
| `smtp-tls`         | boolean | ❌        | Enable TLS for SMTP (default: false)                       |
| `fetch-frequency`  | string  | ❌        | How often to check for new emails (default: `"15m"`)       |
| `sender-whitelist` | array   | ❌        | List of allowed sender email addresses (empty = allow all) |

**Example:**

```json
{
    "channels": [
        {
            "type": "email",
            "email": "kokibot@example.com",
            "username": "kokibot@example.com",
            "password": "${EMAIL_PASSWORD}",
            "imap-host": "imap.gmail.com",
            "imap-port": 993,
            "imap-ssl": true,
            "smtp-host": "smtp.gmail.com",
            "smtp-port": 465,
            "smtp-ssl": true,
            "fetch-frequency": "10m",
            "sender-whitelist": [
                "alice@example.com",
                "bob@example.com"
            ]
        }
    ]
}
```

**Gmail Configuration Notes:**

- Use [App-Specific Passwords](https://support.google.com/accounts/answer/185833) instead of your main password
- Enable IMAP in Gmail settings
- IMAP: `imap.gmail.com:993` (SSL)
- SMTP: `smtp.gmail.com:465` (SSL) or `smtp.gmail.com:587` (TLS)

---

#### WebSocket Channel

Real-time bidirectional communication via WebSocket protocol.

| Parameter | Type   | Required | Description                                             |
|-----------|--------|----------|---------------------------------------------------------|
| `type`    | string | ✅        | Must be `"websocket"`                                   |
| `path`    | string | ❌        | WebSocket endpoint path (default: `"/ws/{agent-name}"`) |

**Example:**

```json
{
    "channels": [
        {
            "type": "websocket",
            "path": "/ws/assistant"
        }
    ]
}
```

**WebSocket Protocol:**

The WebSocket channel uses JSON messages for communication:

**Client → Server:**

```json
{
    "query": "What is the weather today?",
    "userId": "user123",
    "filePaths": []
}
```

**Server → Client (Streaming):**

```json
{
    "type": "REASONING_CHUNK",
    "content": "Analyzing the request...",
    "usage": {
        "totalTokens": 150,
        "promptTokens": 100,
        "completionTokens": 50,
        "promptCacheHitTokens": 20
    }
}
```

```json
{
    "type": "TOOL_STATUS",
    "content": "⚙️ Calling web_search..."
}
```

```json
{
    "type": "FINAL",
    "content": "The weather today is sunny with a high of 75°F.",
    "finishReason": "DONE",
    "contextLength": 2500,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

```json
{
    "type": "ERROR",
    "message": "An error occurred while processing your request"
}
```

**Message Types:**

| Type | Description |
|------|-------------|
| `REASONING_CHUNK` | Streaming reasoning content with optional token usage data |
| `TOOL_STATUS` | Tool execution status updates |
| `FINAL` | Final complete response with context length and conversation id |
| `ERROR` | Error message |

**Conversation Navigation:**

The web client uses the `conversationId` from `FINAL` messages to enable conversation history navigation:

- The id is persisted in `localStorage` as `kokibot_conv_{agentName}`
- On page load, `?conv=<id>` in the URL loads a specific conversation via `GET /assistants/{name}/conversations/{id}`
- After loading, the param is cleaned from the URL with `history.replaceState` (preserving the `agent` param)
- Navigating to `/index.html?agent=<agent>&conv=<id>` via the sidebar triggers a full page load with that conversation

**Conversation History REST Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/assistants/{name}/conversations` | List conversations (`limit`, `offset`, `channelId` params) |
| `GET` | `/assistants/{name}/conversations/{id}` | Conversation detail with messages |

Default `limit` is 30. `userId` is always `"anonymous"` — never a request parameter. `startDate` is returned as an ISO-8601 string.

**Token Usage Information:**

The WebSocket channel streams token usage data in real-time with `REASONING_CHUNK` messages:

- `totalTokens`: Total tokens consumed in this chunk
- `promptTokens`: Tokens in the prompt
- `completionTokens`: Tokens in the completion
- `promptCacheHitTokens`: Cached tokens (cost savings)

**Client-Side Display:**
- Token counts accumulate across all chunks in a message
- Numbers ≥1000 formatted with K suffix (1.5K, 10K, 100K)
- Breakdown shown: prompt, completion, cached
- Example: `3.8K tokens (2.5K prompt, 1.3K completion) 💾 1.2K cached`

**Connection URL:** `ws://localhost:8080{path}`

**Web Interface:**
Access the built-in web interface at `http://localhost:8080/` to interact with agents via WebSocket.

The web UI includes a **conversation history sidebar** listing the last 30 conversations grouped by date:

| Group | Condition |
|---|---|
| Today | conversations started today |
| Yesterday | conversations started yesterday |
| Previous 30 days | 2–30 days ago |
| `yyyy-MM` (e.g. `2026-05`) | older, one group per calendar month |

Clicking a conversation navigates to `/index.html?agent=<agent>&conv=<id>`, loading its full message history. The `settings.html` page shares the same sidebar layout but does not show the conversation history zone.

---

### Memory Configuration

Controls long-term memory compaction and retention.

**Section:** `memory`

| Parameter              | Type    | Default | Description                                                                                                                   |
|------------------------|---------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| `window`               | string  | `"7d"`  | Time window for conversation history to include in compaction. Format: `{number}{unit}` where unit is `d` (days), `h` (hours) |
| `compaction-frequency` | string  | `"6h"`  | How often to run automatic memory compaction. Format: `{number}{unit}`                                                        |
| `max-length`           | integer | `10240` | Maximum length (characters) of the memory file. Prevents unbounded growth                                                     |

**Example:**

```json
{
    "memory": {
        "window": "3d",
        "compaction-frequency": "12h",
        "max-length": 8192
    }
}
```

**How Memory Compaction Works:**

1. Every `compaction-frequency` interval, the assistant:
    - Extracts conversation history from the last `window` period
    - Uses LLM to identify key facts, decisions, and learnings
    - Merges extracted facts with existing `MEMORY.md`
    - Removes outdated or duplicate information
    - Truncates to `max-length` if necessary

2. Memory is stored in: `~/kokibot/agents/{agent-name}/memory/MEMORY.md`

3. Manual compaction: Use the `/compact` command

**Best Practices:**

- **Short-term agents** (chat support): `window: "1d"`, `compaction-frequency: "6h"`
- **Long-term agents** (research): `window: "7d"`, `compaction-frequency: "24h"`
- **High-traffic agents**: Lower `compaction-frequency` to reduce load

---

### Heartbeat Configuration

Schedules periodic automated tasks for the assistant.

**Section:** `heartbeat`

| Parameter   | Type   | Required | Description                                                                                      |
|-------------|--------|----------|--------------------------------------------------------------------------------------------------|
| `frequency` | string | ❌        | How often to run the heartbeat task. Format: `{number}{unit}`. If omitted, heartbeat is disabled |

**Example:**

```json
{
    "heartbeat": {
        "frequency": "1h"
    }
}
```

**How Heartbeat Works:**

1. Every `frequency` interval, the assistant:
    - Reads the content of `~/kokibot/agents/{agent-name}/HEARTBEAT.md`
    - Processes the content as a system message
    - Executes any instructions or tasks defined in the file

2. The `HEARTBEAT.md` file contains instructions for periodic tasks

**Example `HEARTBEAT.md`:**

```markdown
Check for any pending tasks in the workspace and summarize their status.
If there are any critical issues, log them to the daily log.
```

**Common Use Cases:**

- **Monitoring:** Check system health every 30 minutes
- **Reporting:** Generate daily summaries at midnight
- **Cleanup:** Remove old temporary files weekly
- **Reminders:** Send scheduled notifications

**Frequency Examples:**

- `"30m"` = Every 30 minutes
- `"1h"` = Every hour
- `"6h"` = Every 6 hours
- `"1d"` = Once per day

**Note:** Leave `frequency` empty or omit the `heartbeat` section to disable this feature.

---

### Marketplace Configuration

Configures external skill repositories for the assistant.

**Section:** `marketplaces` (array)

| Parameter | Type   | Required | Description                           |
|-----------|--------|----------|---------------------------------------|
| `url`     | string | ✅        | Git repository URL containing skills  |
| `branch`  | string | ❌        | Git branch to use (default: `"main"`) |

**Example:**

```json
{
    "marketplaces": [
        {
            "url": "https://github.com/wutsi/kokibot-skills.git",
            "branch": "main"
        },
        {
            "url": "https://github.com/mycompany/internal-skills.git",
            "branch": "production"
        }
    ]
}
```

**How Marketplaces Work:**

1. On initialization, Kokibot:
    - Clones each marketplace repository to `~/kokibot/agents/{agent-name}/skills/marketplace-{hash}/`
    - Discovers all `SKILL.md` files in the repository
    - Registers skills alongside local skills

2. Skills from marketplaces are treated identically to local skills

3. Repositories are updated periodically (on restart)

**Marketplace Structure:**

```
skill-marketplace/
├── weather/
│   ├── SKILL.md
│   └── scripts/
│       └── get_weather.py
├── calendar/
│   ├── SKILL.md
│   └── scripts/
│       └── sync_events.py
└── README.md
```

**Best Practices:**

- **Private repositories:** Use SSH URLs (`git@github.com:...`) with SSH key authentication
- **Versioning:** Use specific branches or tags for production agents
- **Testing:** Test marketplace skills in a dev agent before production deployment

---

### Swarm Configuration

Controls multi-agent delegation behavior and safety limits.

**Section:** `swarm`

| Parameter       | Type    | Default | Description                                                             |
|-----------------|---------|---------|-------------------------------------------------------------------------|
| `max-depth`     | integer | `5`     | Maximum depth of delegation chains (A→B→C→...). Prevents stack overflow |
| `detect-cycles` | boolean | `true`  | Enable cycle detection to prevent circular delegation (A→B→C→A)         |

**Example:**

```json
{
    "swarm": {
        "max-depth": 10,
        "detect-cycles": true
    }
}
```

**How Swarm Delegation Works:**

1. **Coordinator agent** (with `coordinator: true`) receives a task
2. Coordinator uses `swarm_delegate` tool to delegate subtasks to specialist agents
3. Each delegation increments the depth counter
4. If depth exceeds `max-depth`, delegation is rejected
5. If cycle is detected (e.g., A→B→C→A), delegation is rejected

**Example Delegation Chain:**

```
User → Coordinator → Weather Specialist → API Agent
       (depth 0)     (depth 1)            (depth 2)
```

**Safety Mechanisms:**

- **Max Depth:** Prevents infinite delegation chains
- **Cycle Detection:** Prevents circular delegation loops
- **Session Isolation:** Each user request has independent depth tracking

**Best Practices:**

- **Simple workflows:** `max-depth: 3`
- **Complex workflows:** `max-depth: 5-10`
- **Always enable cycle detection** unless debugging

---

## Tool-Specific Configuration

Some tools support additional configuration via separate files in `config/tools/`.

### Structure

```
~/kokibot/agents/{agent-name}/config/tools/
├── shell.json
├── python.json
└── web_search.json
```

### Example: Shell Tool Configuration

**File:** `config/tools/shell.json`

```json
{
    "timeout-seconds": 10,
    "blacklist": [
        "rm -rf",
        "sudo",
        "chmod",
        "chown"
    ]
}
```

### Example: Python Tool Configuration

**File:** `config/tools/python.json`

```json
{
    "timeout-seconds": 30,
    "max-memory-mb": 512
}
```

### Example: Web Search Tool Configuration

**File:** `config/tools/web_search.json`

```json
{
    "api-key": "${SEARCH_API_KEY}",
    "max-results": 5,
    "timeout-millis": 5000
}
```

**Note:** Tool-specific configurations are optional. Tools use sensible defaults if not configured.

---

## Advanced Configuration Patterns

### Multi-Agent Setup (Coordinator + Specialists)

**Coordinator Agent** (`~/kokibot/agents/coordinator/config/settings.json`):

```json
{
    "assistant": {
        "coordinator": true,
        "max-iterations": 15,
        "description": "Main coordinator for task delegation"
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
    ]
}
```

**Specialist Agent** (`~/kokibot/agents/weather-specialist/config/settings.json`):

```json
{
    "assistant": {
        "max-iterations": 5,
        "description": "Weather forecasting specialist"
    },
    "llm": {
        "type": "deepseek",
        "api-key": "${DEEPSEEK_API_KEY}",
        "model": "deepseek-v4-flash"
    },
    "channels": []
}
```

**Key Points:**

- Coordinator has `coordinator: true` and channels
- Specialists have no channels (internal-only)
- Each agent has isolated configuration and workspace

---

### Development vs Production Profiles

**Development Configuration:**

```json
{
    "assistant": {
        "max-iterations": 20,
        "max-duration": "10m"
    },
    "llm": {
        "type": "deepseek",
        "model": "deepseek-chat",
        "temperature": 0.9
    },
    "memory": {
        "compaction-frequency": "1h"
    }
}
```

**Production Configuration:**

```json
{
    "assistant": {
        "max-iterations": 10,
        "max-duration": "5m"
    },
    "llm": {
        "type": "deepseek",
        "model": "deepseek-v4-flash",
        "temperature": 0.5
    },
    "memory": {
        "compaction-frequency": "6h"
    }
}
```

**Differences:**

- **Development:** More iterations, higher creativity, frequent compaction
- **Production:** Lower limits for stability, faster model, less frequent compaction

---

## Validation and Troubleshooting

### Configuration Validation

Kokibot validates configuration on startup. Common errors:

| Error                                             | Cause                          | Solution                                          |
|---------------------------------------------------|--------------------------------|---------------------------------------------------|
| `ConfigurationException: llm.api-key is required` | Missing or invalid LLM API key | Set environment variable or provide key in config |
| `ConfigurationException: token is required`       | Missing Telegram bot token     | Provide `token` in channel config                 |
| `ChannelNotFoundException`                        | Invalid channel type           | Use `telegram`, `email`, or `websocket`           |
| `No agents/ directory`                            | Missing agents directory       | Create `~/kokibot/agents/` directory              |

### Health Check

Use the `/health` command to check system status:

```
User: /health
Assistant:
✅ context
  ✅ llm:deepseek
  ✅ channel:telegram
  ✅ service:memory
  ✅ service:heartbeat
  ✅ 5 tools
  ✅ 3 skills
```

### Logging

Logs are stored in:

- **Console:** Standard output during development
- **File:** `~/kokibot/logs/kokibot.log` (production)

Increase log level by setting environment variable:

```bash
export LOG_LEVEL=DEBUG
```

---

## Technical Details

### Streaming Architecture

The streaming system uses a layered architecture to pass token usage data from the LLM to the UI:

**Data Flow:**

```
LLM Provider (Deepseek/Kimi/Gemini)
    ↓ streams text + usage
LLMStreamData { text, usage }
    ↓
ReasoningLoop (ReActReasoningLoop)
    ↓ wraps in StreamData
Assistant.process()
    ↓ delegates callback
Channel (WebSocket/Telegram)
    ↓ extracts and formats
WebSocketResponse { type, content, usage }
    ↓ sends JSON over WebSocket
Web Client (chat-ui.js)
    ↓ accumulates and formats
UI Display: "3.8K tokens (2.5K prompt, 1.3K completion)"
```

**Key Components:**

1. **LLMStreamData**: Data class containing `text` + `usage` (backend)
2. **StreamData callback**: `((LLMStreamData) -> Unit)?` passed through call chain
3. **WebSocketResponse**: JSON message with `usage` field
4. **Token Accumulator**: JavaScript state tracking cumulative usage
5. **K-Formatter**: Display helper for human-readable numbers

**Reasoning Text Rendering:**

Reasoning content blocks preserve line breaks using:
- HTML escaping for security (`<`, `>`, `&`, etc.)
- `\n` → `<br>` conversion for line breaks
- CSS `white-space: pre-wrap` for proper formatting
- Separate rendering from final response (which uses Markdown)

**Security:**
- All user content HTML-escaped to prevent XSS
- Only safe HTML (`<br>` tags) injected
- No eval() or innerHTML without sanitization

---

## See Also

- [README.md](../README.md) - Project overview and quick start
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture details
- [CLAUDE.md](../CLAUDE.md) - Development guidelines for Claude Code

---

[← Back to Documentation](../README.md)
