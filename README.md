# Kokibot

An extensible AI assistant framework built with Kotlin and Spring Boot that provides pluggable architecture for LLM providers, communication channels, and custom tools.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![JaCoCo Coverage](https://img.shields.io/badge/Coverage-92%25-brightgreen.svg)](target/site/jacoco/index.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg?logo=spring)](https://spring.io/projects/spring-boot)

## Features

- **Iterative Reasoning**: Multi-step reasoning loop with configurable max iterations
- **Tool System**: Extensible tool architecture with 10+ built-in tools
- **Skills System**: Modular, keyword-activated extensions for domain-specific capabilities
- **Multiple Channels**: Support for Telegram (extensible to other platforms)
- **Long-Term Memory**: Automatic conversation compaction and fact extraction
- **Email Integration**: Send, read, search, and manage emails via SMTP/IMAP
- **Code Execution**: Run Python code and shell commands safely
- **Web Capabilities**: Search the web and fetch content from URLs
- **Commands**: Built-in commands for system control and debugging

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **GraalVM** (for Python tool support)
- **Environment Variables** (for LLM and channel integrations):
  - `DEEPSEEK_API_KEY` - API key for Deepseek LLM
  - `TELEGRAM_TOKEN` - Bot token for Telegram integration (optional)
  - `MAIL_USERNAME` / `MAIL_PASSWORD` - Email credentials (optional)

## Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/wutsi/kokibot.git
cd kokibot
mvn clean install
```

### 2. Setup Configuration

Create the configuration directory and file:

```bash
mkdir -p ~/kokibot/config
cat > ~/kokibot/config/settings.json << 'EOF'
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
  }
}
EOF
```

### 3. Run the Application

```bash
export DEEPSEEK_API_KEY="your-api-key"
export TELEGRAM_TOKEN="your-telegram-token"
mvn spring-boot:run
```

## Configuration

### Directory Structure

```
~/kokibot/
├── config/
│   ├── settings.json          # Main configuration
│   └── tools/                 # Per-tool config (optional)
│       └── {tool-name}.json
├── skills/                    # Custom skills
│   └── {skill-name}/
│       └── SKILL.md
├── workspace/
│   ├── history/
│   │   └── history.json       # Conversation history
│   └── memory/
│       └── MEMORY.md          # Long-term memory
└── AGENT.md                   # System instructions (optional)
```

### Configuration Options

#### Assistant Configuration

```json
{
  "assistant": {
    "max-iterations": 10  // Maximum reasoning loop iterations
  }
}
```

#### LLM Configuration

Currently supports Deepseek:

```json
{
  "llm": {
    "type": "deepseek",
    "api-key": "${DEEPSEEK_API_KEY}",
    "model": "deepseek-chat"
  }
}
```

#### Email Configuration

```json
{
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

#### Memory Configuration

```json
{
  "memory": {
    "window": 3,                    // Days of history to compact
    "compaction-frequency": 6       // Hours between compaction runs
  }
}
```

## Built-in Tools

Kokibot comes with 10+ built-in tools:

| Tool | Description |
|------|-------------|
| `clock` | Get current date and time |
| `web_search` | Search the web via Brave Search API |
| `web_fetch` | Fetch content from URLs (HTML, PDF) |
| `python` | Execute Python code safely |
| `shell` | Run shell commands with security restrictions |
| `mail_list` | List emails from mailbox |
| `mail_read` | Read email content |
| `mail_send` | Send emails or replies |
| `mail_find` | Search emails by criteria |
| `mail_unsubscribe` | Unsubscribe from mailing lists |

## Commands

Commands are invoked with `/command` syntax:

- `/help` - Display available commands
- `/clear` - Clear conversation history
- `/compact` - Manually trigger memory compaction
- `/skill [name]` - Show skill details or list all skills
- `/tool [name]` - Show tool details or list all tools

## Skills System

Skills are modular extensions that activate dynamically based on user intent.

### Creating a Custom Skill

1. Create a skill directory:

```bash
mkdir -p ~/kokibot/skills/my-skill
```

2. Create `SKILL.md`:

```markdown
---
name: my-skill
description: Brief description of your skill
requires:
    bins: []
    env: []
metadata:
    keywords: ["keyword1", "keyword2"]
    categories: ["category"]
---

# My Skill

Detailed description

## Tools

- `my_tool`: Tool description
    - `param1`: (string) Parameter description
    - `param2`: (int) Optional parameter

## Instructions

Guidelines for the assistant

## Examples

User: "Example query"
Action: Call `my_tool(param1="value")`
```

3. Restart Kokibot - the skill will be automatically discovered

### Skill Activation

Skills automatically activate when:
- User query matches skill keywords
- Activated skills' tools are made available to the LLM
- Skill instructions are injected into system prompt

## Architecture

### Core Components

- **Assistant**: Main reasoning loop with tool execution
- **Context**: System state container passed to all components
- **ToolRegistry**: Manages available tools
- **SkillRegistry**: Discovers and activates skills
- **CommandRegistry**: Handles system commands
- **ChatHistory**: Persists conversation history
- **Memory**: Long-term fact extraction and storage

### LLM Integration

Pluggable LLM providers via factory pattern. Currently supports:
- Deepseek (with function calling support)

### Channels

Communication interfaces for the assistant:
- Telegram (via long polling)
- Extensible to other platforms

## Development

### Build and Test

```bash
# Full build with tests
mvn clean install

# Run tests only
mvn test

# Run specific test
mvn test -Dtest=AssistantTest#process

# View coverage report
open target/site/jacoco/index.html
```

### Code Quality

```bash
# Lint Kotlin code
mvn antrun:run@ktlint

# Auto-format code
mvn antrun:run@ktlint-format
```

### Code Coverage Requirements

- **Line Coverage**: 92% minimum
- **Class Coverage**: 92% minimum

### Adding a New Tool

1. Create a class implementing `Tool` interface:

```kotlin
class MyTool : Tool {
    override fun metadata(): ToolMetadata = ToolMetadata(
        name = "my_tool",
        description = "Description of what the tool does",
        parameters = listOf(
            ToolParameter(
                name = "param1",
                description = "Parameter description",
                type = ToolParameterType.STRING,
                required = true
            )
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val param1 = arguments["param1"]?.toString()
        // Implementation
        return "Result"
    }
}
```

2. Register in `ContextFactory.discoverTools()`:

```kotlin
private fun discoverTools(): List<Tool> {
    return listOf(
        // ... existing tools
        MyTool(),
    )
}
```

### Adding a New Channel

1. Create a class extending `Channel`:

```kotlin
class MyChannel(agent: Assistant) : Channel(agent) {
    override fun init(config: Map<*, *>) {
        // Initialize channel
    }

    override fun destroy() {
        // Cleanup
    }
}
```

2. Register in `ChannelFactory.create()`:

```kotlin
fun create(type: String, agent: Assistant): Channel {
    return when (type.lowercase()) {
        "telegram" -> TelegramChannel(agent)
        "mychannel" -> MyChannel(agent)
        else -> throw ConfigurationException("Unknown channel type: $type")
    }
}
```

3. Add configuration to `settings.json`

## Security

- **Shell Tool**: Blocks dangerous commands (`sudo`, `rm -rf`, etc.)
- **Python Tool**: Runs in sandboxed GraalVM context
- **Command Timeout**: 5-second default timeout for shell commands
- **Environment Variables**: Sensitive data stored as environment variables

## Technology Stack

- **Language**: Kotlin 2.2.0
- **Framework**: Spring Boot 4.0.5
- **LLM Client**: Custom REST client
- **Python Engine**: GraalVM Polyglot 25.0.2
- **Email**: Jakarta Mail API 2.1.5
- **Telegram**: Telegram Bots SDK 9.5.0
- **HTML Parsing**: JSoup 1.22.1
- **PDF Parsing**: Apache PDFBox 3.0.6
- **Testing**: JUnit 5, Mockito Kotlin

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or contributions, please open an issue on GitHub.

## Acknowledgments

- Built with [Spring Boot](https://spring.io/projects/spring-boot)
- LLM integration via [Deepseek](https://www.deepseek.com/)
- Telegram integration via [Telegram Bots](https://github.com/rubenlagus/TelegramBots)
