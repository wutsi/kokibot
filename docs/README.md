# Kokibot Documentation

Welcome to the Kokibot documentation! This guide provides comprehensive information about building, deploying, and extending Kokibot.

## 📚 Documentation Structure

### Getting Started
- **[Setup Guide](SETUP.md)** - Installation instructions for different environments and platforms

### Technical Documentation
- **[Architecture](ARCHITECTURE.md)** - System architecture, components, and data flow
- **[References](references/)** - Detailed API references and guides
  - [Commands](references/commands.md) - Built-in commands and usage
  - [Tools](references/tools.md) - Available tools and their parameters
  - [Skills](references/skills.md) - Skills system and how to create custom skills
  - [LLM](references/llm.md) - LLM integration, configuration, and tuning
  - [Mail](references/mail.md) - Email system configuration and usage
  - [Heartbeat](references/heartbeat.md) - Automated task scheduling and monitoring

### Quick Links
- [Main README](../README.md) - Project overview and quick start
- [AGENT.md](../AGENT.md) - Developer guide for contributors

---

## 🎯 What is Kokibot?

Kokibot is an extensible AI assistant framework built with Kotlin and Spring Boot. It provides a production-ready platform for building AI assistants that can:

- Execute multi-step reasoning tasks
- Use tools to interact with external systems
- Extend capabilities through modular skills
- Communicate via multiple channels (Telegram, and more)
- Maintain long-term memory and conversation context

---

## 🚀 Quick Navigation

### For Users
- **First time?** Start with the [Setup Guide](SETUP.md)
- **Using commands?** Check the [Commands Reference](references/commands.md)
- **Need help?** See available [Tools](references/tools.md)
- **Email integration?** See the [Mail Reference](references/mail.md)
- **Automated tasks?** Learn about [Heartbeat](references/heartbeat.md)

### For Developers
- **Understanding the system?** Read the [Architecture](ARCHITECTURE.md)
- **Building skills?** Follow the [Skills Guide](references/skills.md)
- **LLM integration?** Check the [LLM Reference](references/llm.md)
- **Contributing?** See the [Developer Guide](../AGENT.md)

---

## 🔍 Common Tasks

### Configuration
```bash
# Configuration location
~/kokibot/config/settings.json

# Skills directory
~/kokibot/skills/

# Workspace (history, memory)
~/kokibot/workspace/
```

### Running Kokibot
```bash
# Build and run
mvn clean install
mvn spring-boot:run

# Run with environment variables
export DEEPSEEK_API_KEY="your-key"
export TELEGRAM_TOKEN="your-token"
mvn spring-boot:run
```

### Development
```bash
# Run tests
mvn test

# Code formatting
mvn antrun:run@ktlint-format

# Coverage report
mvn test && open target/site/jacoco/index.html
```

---

## 📖 Documentation Conventions

Throughout this documentation:
- `~/kokibot/` refers to the Kokibot home directory (defaults to `$HOME/kokibot`)
- Code blocks show example commands or configuration
- 💡 Tips provide additional helpful information
- ⚠️ Warnings highlight important considerations

---

## 🆘 Getting Help

- **Issues:** Report bugs on [GitHub Issues](https://github.com/wutsi/kokibot/issues)
- **Discussions:** Ask questions on [GitHub Discussions](https://github.com/wutsi/kokibot/discussions)
- **Documentation:** Browse this docs directory for detailed guides

---

## 📝 Documentation Version

This documentation corresponds to:
- **Version:** 0.0.1-SNAPSHOT
- **Kotlin:** 2.2.0
- **Spring Boot:** 4.0.5
- **Java:** 17

---

[Back to Main README](../README.md) | [Architecture →](ARCHITECTURE.md)
