<div align="center">

# Kokibot

### Your Extensible AI Assistant Framework

*Build production-ready AI assistants with pluggable architecture for LLM providers, communication channels, and custom tools*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg?logo=spring)](https://spring.io/projects/spring-boot)
[![Master Build](https://github.com/wutsi/kokibot/actions/workflows/_master.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/_master.yml)
[![PR Build](https://github.com/wutsi/kokibot/actions/workflows/_pr.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/_pr.yml)
[![JaCoCo Coverage](https://img.shields.io/badge/Coverage-93%25-brightgreen.svg)](target/site/jacoco/index.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Features](#-key-features) • [Quick Start](#-quick-start) • [Documentation](#-documentation) • [Contributing](#-contributing)

</div>

---

## 📖 Introduction

**Kokibot** is a powerful, extensible AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable architecture that makes it easy to build production-ready AI assistants capable of complex reasoning, tool execution, and multi-channel communication.

### The Problem

Traditional chatbots are limited to basic query-response patterns. Building AI assistants that can execute multi-step reasoning tasks, interact with external systems (email, web, databases), maintain conversation context and long-term memory, extend capabilities through modular plugins, and support multiple communication channels requires significant engineering effort and architectural planning.

### The Solution

Kokibot solves these challenges by providing a **production-ready framework** with:

- **Iterative Reasoning Engine** - Multi-step reasoning loop that breaks down complex queries into manageable steps
- **Pluggable Tool System** - 10+ built-in tools with easy extensibility for custom capabilities
- **Dynamic Skills System** - Modular, keyword-activated extensions for domain-specific functionality
- **Long-Term Memory** - Automatic conversation compaction and intelligent fact extraction
- **Multi-Channel Support** - Telegram integration with extensible architecture for other platforms
- **Enterprise-Ready Architecture** - Built on Spring Boot with comprehensive testing and security features

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **Iterative Reasoning** | Multi-step reasoning loop with configurable max iterations |
| **Tool System** | 10+ built-in tools: web search, code execution, email management, and more |
| **Skills System** | Modular, keyword-activated extensions for domain-specific capabilities |
| **Multiple Channels** | Telegram support with extensible architecture for additional platforms |
| **Long-Term Memory** | Automatic conversation compaction and fact extraction |
| **Email Integration** | Full SMTP/IMAP support for sending, reading, searching, and managing emails |
| **Code Execution** | Safe Python and shell command execution with security restrictions |
| **Web Capabilities** | Web search and content extraction from URLs (HTML, PDF) |
| **Commands** | Built-in commands for system control, debugging, and health monitoring |
| **Configuration** | Flexible JSON-based configuration with environment variable support |

---

## 🛠 Technology Stack

| Category | Technologies |
|----------|-------------|
| **Language** | Kotlin 2.2.0 |
| **Framework** | Spring Boot 4.0.5 |
| **LLM Integration** | Custom REST client (Deepseek support) |
| **Python Engine** | GraalVM Polyglot 25.0.2 |
| **Email** | Jakarta Mail API 2.1.5 |
| **Messaging** | Telegram Bots SDK 9.5.0 |
| **HTML Parsing** | JSoup 1.22.1 |
| **PDF Parsing** | Apache PDFBox 3.0.6 |
| **Testing** | JUnit 5, Mockito Kotlin, GreenMail |

---

## 📋 Prerequisites

Before getting started, ensure you have:

- **Java 17** or higher
- **Maven 3.6+** for building the project
- **GraalVM** (optional, for Python tool support)
- **API Keys & Credentials**:
  - `DEEPSEEK_API_KEY` - API key for Deepseek LLM (required)
  - `TELEGRAM_TOKEN` - Bot token for Telegram integration (optional)
  - `MAIL_USERNAME` / `MAIL_PASSWORD` - Email credentials for mail tools (optional)

---

## 🚀 Quick Start

### Step 1: Clone the Repository

```bash
git clone https://github.com/wutsi/kokibot.git
cd kokibot
```

### Step 2: Build the Project

```bash
mvn clean install
```

### Step 3: Setup Configuration

Create your configuration directory and settings file:

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

### Step 4: Run the Application

```bash
export DEEPSEEK_API_KEY="your-api-key"
export TELEGRAM_TOKEN="your-telegram-token"
mvn spring-boot:run
```

That's it! Your Kokibot instance is now running and ready to receive messages.

---

## 📚 Documentation

For detailed technical documentation, architecture guides, and implementation details, please refer to:

- **[AGENT.md](AGENT.md)** - Complete architecture documentation, component overview, and development guide
- **[Built-in Tools](#-built-in-tools)** - See below for tool reference
- **[Skills System](#-skills-system)** - Learn about creating custom skills
- **[Commands Reference](#-commands)** - Available system commands

---

## 🔧 Configuration

### Directory Structure

```
~/kokibot/
├── config/
│   ├── settings.json          # Main configuration
│   └── tools/                 # Per-tool config (optional)
│       └── {tool-name}.json
├── skills/                    # Custom skills
│   └── {skill-name}/
│       ├── SKILL.md           # Skill definition
│       └── scripts/           # Tool implementations
├── workspace/
│   ├── history/               # Conversation history
│   └── memory/                # Long-term memory
└── AGENT.md                   # System instructions (optional)
```

### Configuration Examples

<details>
<summary><b>Assistant Configuration</b></summary>

```json
{
  "assistant": {
    "max-iterations": 10
  }
}
```
</details>

<details>
<summary><b>LLM Configuration</b></summary>

```json
{
  "llm": {
    "type": "deepseek",
    "api-key": "${DEEPSEEK_API_KEY}",
    "model": "deepseek-chat"
  }
}
```
</details>

<details>
<summary><b>Email Configuration</b></summary>

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
</details>

<details>
<summary><b>Memory Configuration</b></summary>

```json
{
  "memory": {
    "window": 3,
    "compaction-frequency": 6
  }
}
```
</details>

---

## 🧰 Built-in Tools

Kokibot comes with 10+ production-ready tools:

| Tool | Description | Example Use Case |
|------|-------------|------------------|
| `clock` | Get current date and time | "What's the current date?" |
| `web_search` | Search the web via Brave Search API | "Search for latest Kotlin news" |
| `web_fetch` | Fetch content from URLs (HTML, PDF) | "Summarize this article" |
| `python` | Execute Python code safely | "Calculate fibonacci(100)" |
| `shell` | Run shell commands with restrictions | "List files in current directory" |
| `mail_list` | List emails from mailbox | "Show my recent emails" |
| `mail_read` | Read email content | "Read email with ID 12345" |
| `mail_send` | Send emails or replies | "Send email to john@example.com" |
| `mail_find` | Search emails by criteria | "Find emails from boss this week" |
| `mail_unsubscribe` | Unsubscribe from mailing lists | "Unsubscribe from this newsletter" |

---

## 💡 Skills System

Skills are modular extensions that activate dynamically based on user intent. They provide domain-specific capabilities through custom tools and instructions.

### Creating a Custom Skill

**Step 1:** Create a skill directory

```bash
mkdir -p ~/kokibot/skills/my-skill
```

**Step 2:** Create `SKILL.md` with frontmatter

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

Detailed description of what this skill does.

## Tools

- `my_tool`: Tool description
    - `param1`: (string) Parameter description
    - `param2`: (int) Optional parameter

## Instructions

Guidelines for the assistant on how to use this skill effectively.

## Examples

User: "Example query that triggers this skill"
Action: Call `my_tool(param1="value")`
```

**Step 3:** Restart Kokibot

The skill will be automatically discovered and made available.

### How Skills Work

- **Automatic Activation**: Skills activate when user queries match configured keywords
- **Tool Registration**: Activated skills' tools are made available to the LLM
- **Context Injection**: Skill instructions are injected into the system prompt

---

## 🎮 Commands

Commands provide system-level functionality via `/command` syntax:

| Command | Description |
|---------|-------------|
| `/help [cmd]` | Display available commands or show details for a specific command |
| `/clear` | Clear conversation history |
| `/compact` | Manually trigger memory compaction |
| `/health` | System health check for all components |
| `/skill [name]` | Show skill details or list all available skills |
| `/tool [name]` | Show tool details or list all available tools |

---

## 🏗 Architecture Overview

### Core Components

- **Assistant** - Main reasoning loop with tool execution orchestration
- **Context** - System state container passed to all components
- **ToolRegistry** - Manages and provides access to available tools
- **SkillRegistry** - Discovers and activates skills dynamically
- **CommandRegistry** - Handles system-level commands
- **ChatHistory** - Persists and retrieves conversation history
- **Memory** - Extracts and stores long-term facts from conversations

### LLM Integration

Pluggable LLM provider architecture via factory pattern:
- Currently supports **Deepseek** with function calling
- Extensible to other LLM providers

### Channels

Communication interfaces for the assistant:
- **Telegram** - Long polling integration
- **Extensible** - Add custom channels via the `Channel` abstract class

For detailed architecture documentation, see [AGENT.md](AGENT.md).

---

## 👨‍💻 Development

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

**Coverage Requirements**: 93% minimum for both line and class coverage

### Adding a New Tool

**Step 1:** Implement the `Tool` interface

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

**Step 2:** Register in `ContextFactory.discoverTools()`

```kotlin
private fun discoverTools(): List<Tool> {
    return listOf(
        // ... existing tools
        MyTool(),
    )
}
```

### Adding a New Channel

**Step 1:** Extend the `Channel` class

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

**Step 2:** Register in `ChannelFactory.create()`

```kotlin
fun create(type: String, agent: Assistant): Channel {
    return when (type.lowercase()) {
        "telegram" -> TelegramChannel(agent)
        "mychannel" -> MyChannel(agent)
        else -> throw ConfigurationException("Unknown channel type: $type")
    }
}
```

**Step 3:** Add configuration to `settings.json`

---

## 🔒 Security

Kokibot implements multiple security measures:

- **Shell Tool Security** - Blocks dangerous commands (`sudo`, `rm -rf`, `chmod`, `chown`, etc.)
- **Python Sandboxing** - Executes Python code in isolated GraalVM context
- **Command Timeouts** - 5-second default timeout for shell commands
- **Environment Variables** - Sensitive data stored as environment variables, never in code
- **No Code Execution on User Input** - All tool execution goes through validated interfaces

---

## 🗺 Roadmap

### Current Version (v0.0.1-SNAPSHOT)

- ✅ Core iterative reasoning engine
- ✅ 10+ built-in tools
- ✅ Skills system with dynamic activation
- ✅ Telegram channel support
- ✅ Long-term memory with automatic compaction
- ✅ Email integration (SMTP/IMAP)

### Upcoming Features

- 🔄 Additional LLM provider support (OpenAI, Anthropic, etc.)
- 🔄 Slack and Discord channel integrations
- 🔄 Database integration tools
- 🔄 Advanced scheduling and automation capabilities
- 🔄 Web UI for configuration and monitoring
- 🔄 Plugin marketplace for community-contributed skills

### Future Vision

- 🎯 Multi-agent collaboration
- 🎯 Visual workflow builder
- 🎯 Enhanced memory with semantic search
- 🎯 Fine-tuning support for domain-specific models

Contributions for roadmap features are welcome! Check our [Contributing](#-contributing) section.

---

## 🤝 Contributing

We welcome contributions from the community! Here's how to get started:

### Getting Started

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Make your changes**
4. **Run tests and ensure quality**
   ```bash
   mvn test
   mvn antrun:run@ktlint
   ```
5. **Commit your changes**
   ```bash
   git commit -m 'Add amazing feature'
   ```
6. **Push to your fork**
   ```bash
   git push origin feature/amazing-feature
   ```
7. **Open a Pull Request**

### Contribution Guidelines

- ✅ Ensure all tests pass
- ✅ Maintain 93% code coverage
- ✅ Follow ktlint code style
- ✅ Add tests for new features
- ✅ Update documentation as needed
- ✅ Write clear commit messages

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 💬 Support

Need help? Here's how to get support:

- **Issues**: Open an issue on [GitHub Issues](https://github.com/wutsi/kokibot/issues)
- **Questions**: Start a discussion on [GitHub Discussions](https://github.com/wutsi/kokibot/discussions)
- **Documentation**: Check [AGENT.md](AGENT.md) for technical details

---

## 🙏 Acknowledgments

Kokibot is built with excellent open-source technologies:

- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework
- [Kotlin](https://kotlinlang.org/) - Primary programming language
- [Deepseek](https://www.deepseek.com/) - LLM integration
- [Telegram Bots](https://github.com/rubenlagus/TelegramBots) - Telegram integration
- [GraalVM](https://www.graalvm.org/) - Python execution engine

---

<div align="center">

**Built with ❤️ by the Kokibot team**

[⭐ Star us on GitHub](https://github.com/wutsi/kokibot) • [🐛 Report a Bug](https://github.com/wutsi/kokibot/issues) • [💡 Request a Feature](https://github.com/wutsi/kokibot/issues)

</div>
