<div align="center">

# Kokibot

### Your Extensible AI Assistant Framework

*Build production-ready AI assistants with pluggable architecture for LLM providers, communication channels, and custom
tools*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg?logo=spring)](https://spring.io/projects/spring-boot)
[![Master Build](https://github.com/wutsi/kokibot/actions/workflows/master.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/master.yml)
[![PR Build](https://github.com/wutsi/kokibot/actions/workflows/pr.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/pr.yml)
[![JaCoCo Coverage](https://img.shields.io/badge/Coverage-93%25-brightgreen.svg)](target/site/jacoco/index.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Features](#-key-features) • [Quick Start](#-quick-start) • [Documentation](#-documentation)

</div>

---

## 📖 Introduction

**Kokibot** is a powerful, extensible AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable
architecture that makes it easy to build production-ready AI assistants capable of complex reasoning, tool execution,
and multi-channel communication.

### The Problem

Building AI assistants that can execute multi-step reasoning tasks, interact with external systems (email, web,
databases), maintain conversation context and long-term memory, extend capabilities through modular plugins, and support
multiple communication channels requires significant engineering effort and architectural planning.

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

| Feature                 | Description                                                                 |
|-------------------------|-----------------------------------------------------------------------------|
| **Iterative Reasoning** | Multi-step reasoning loop with configurable max iterations                  |
| **Tool System**         | 10+ built-in tools: web search, code execution, email management, and more  |
| **Skills System**       | Modular, keyword-activated extensions for domain-specific capabilities      |
| **Multiple Channels**   | Telegram support with extensible architecture for additional platforms      |
| **Long-Term Memory**    | Automatic conversation compaction and fact extraction                       |
| **Email Integration**   | Full SMTP/IMAP support for sending, reading, searching, and managing emails |
| **Code Execution**      | Safe Python and shell command execution with security restrictions          |
| **Web Capabilities**    | Web search and content extraction from URLs (HTML, PDF)                     |
| **Commands**            | Built-in commands for system control, debugging, and health monitoring      |
| **Configuration**       | Flexible JSON-based configuration with environment variable support         |

---

## 🛠 Technology Stack

| Category            | Technologies                                |
|---------------------|---------------------------------------------|
| **Language**        | Kotlin 2.2.0, Java 17                       |
| **Framework**       | Spring Boot 4.0.5                           |
| **LLM Integration** | Custom REST client (Deepseek, Kimi support) |
| **Python Engine**   | GraalVM Polyglot 25.0.2                     |
| **Email**           | Jakarta Mail API 2.1.5                      |
| **Messaging**       | Telegram Bots SDK 9.5.0                     |
| **HTML Parsing**    | JSoup 1.22.1                                |
| **PDF Parsing**     | Apache PDFBox 3.0.6                         |
| **Testing**         | JUnit 5, Mockito Kotlin, GreenMail          |

---

## 🚀 Quick Start

### Step 1: Install Kokibot (macOS & Linux)

Run the following command in your terminal:

```bash
curl -fsSL https://github.com/wutsi/kokibot/releases/latest/download/install.sh | bash
```

This will automatically download and install Kokibot as a background service using the appropriate method for your
platform (`launchd` for macOS, `systemd` for Linux).

### Step 2: Set Environment Variables

#### Setup the LLM (REQUIRED)

You must setup environment variables to enable the _brain_ of your AI assistant.

```bash
export KOKIBOT_LLM_TYPE="your-llm-api-key"
export KOKIBOT_LLM_API_KEY="your-llm-api-key"
export KOKIBOT_LLM_MODEL="your-llm-model"
```

The LLM supported:

- Deepseek: `export KOKIBOT_LLM_TYPE=deepseek`
- Kimi: `export KOKIBOT_LLM_TYPE=kimi`

#### Setup the Channel (REQUIRED)

You must setup environment variables so that you can interact with your AI assistant.

```bash
export KOKIBOT_CHANNEL_TYPE="your-channel-type"
export KOKIBOT_TOKEN="your-channel-token"
```

The channel supported are:

- Telegram: `export KOKIBOT_CHANNEL_TYPE=telegram`

### Step 3 Relaunch KokiBot

#### iOS

```bash
aunchctl unload ~/Library/LaunchAgents/com.kokibot.service.plist
launchctl load ~/Library/LaunchAgents/com.kokibot.service.plist
```

#### Linux

```bash
systemctl --user stop kokibot.service
systemctl --user start kokibot.service
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ by the Kokibot team**

[⭐ Star us on GitHub](https://github.com/wutsi/kokibot) • [🐛 Report a Bug](https://github.com/wutsi/kokibot/issues) • [💡 Request a Feature](https://github.com/wutsi/kokibot/issues)

</div>
