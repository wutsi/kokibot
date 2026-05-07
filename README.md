<div align="center">

# Kokibot

### Your Extensible AI Assistant Framework

*Build production-ready AI assistants with pluggable architecture for LLM providers, communication channels, and custom
tools*

[![release](https://github.com/wutsi/kokibot/actions/workflows/release.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/release.yml)
[![master](https://github.com/wutsi/kokibot/actions/workflows/master.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/master.yml)
[![pr](https://github.com/wutsi/kokibot/actions/workflows/pr.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/pr.yml)
[![JaCoCo Coverage](https://img.shields.io/badge/Coverage-93%25-brightgreen.svg)](target/site/jacoco/index.html)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Python](https://img.shields.io/badge/Python-3.x-orange.svg?logo=python)](https://python.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg?logo=spring)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Features](#-key-features) • [Quick Start](#-quick-start) • [Documentation](#-documentation)

</div>

---

## Introduction

**Kokibot** is a powerful, extensible AI assistant framework built with Kotlin and Spring Boot. It provides a pluggable
architecture that makes it easy to build production-ready AI assistants capable of complex reasoning, tool execution,
and multi-channel communication.

---

## Quick Start

### Step 0: Prerequisites

- Homebrew (for macOS and Linux)
- Java 17 or higher
- Python 3.x (optional, for tools that require Python)

### Step 1: Set Environment Variables

Setup the following environment variables:

```bash
export KOKIBOT_LLM_TYPE="your-llm-api-key"        // Type of LLM: deepseek, kimi, gemini
export KOKIBOT_LLM_API_KEY="your-llm-api-key"     // LLM API Key
export KOKIBOT_LLM_MODEL="your-llm-model"         // LLM Model

export KOKIBOT_CHANNEL_TYPE="your-channel-type"   // Type of channel: telegram
export KOKIBOT_TOKEN="your-channel-token"         // Channel token: e.g., Telegram Bot Token
```

### Step 2: Install Kokibot (macOS & Linux)

Run the following command in your terminal:

```bash
curl -fsSL https://github.com/wutsi/kokibot/releases/latest/download/install.sh | bash
```

This will automatically

- Download the installation files
- Install kokibot
    - The binaries will be installed to `~/Application/kokikot`
    - The configuration, logs and data will be stored in `~/.kokibot`
- Run it a background service  (with `launchd` for macOS, `systemd` for Linux).

---

## Documentation

### Directory Structure

```
.kokibot/
   agents/
     {agent-name}/                          # Directory for each agent
       ASSISTANT.md                         # Assistant instructions
       HEARTBEAT.md                         # Instruction of the heartbeat task executed frequently every day (Ex: ever hour)
       config/
         settings.json                      # Main configuration file for kokibot
         instructions/                      # Directory containing additional instructions for the agent
         tools/                             # Directory containing additional tools for the agent
            {tool-name}.json                # Tool configuration file
       skills/
         {skill-name}/                      # Directory for each skill
           SKILL.md                         # Skill instructions file
       memory/
         MEMORY.md                          # Agent long term memory. Maintained by the agent itself
         history/
           {yyyy}-{MM}-{dd}.log             # Daily conversation history. Maintained by the agent itself
       workspace/                           # Working directory
         files/                             # Directory for files created by tools/skillsv
         tmp/                               # Temporary directory for intermediate files
   logs/                                    # Log directory
     kokibot.log                            # Today's log file
     kokibot-{yyyy}-{MM}-{dd}.log           # Archive of daily log files
```

### Supported LLM Providers

- Deepseek v3, v4
- Kimi
- Gemini

### Supported Communication Channels

- Telegram
- Email

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ by the Kokibot team**

[⭐ Star us on GitHub](https://github.com/wutsi/kokibot) • [🐛 Report a Bug](https://github.com/wutsi/kokibot/issues) • [💡 Request a Feature](https://github.com/wutsi/kokibot/issues)

</div>
