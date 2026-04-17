<div align="center">

# Kokibot

### Your Extensible AI Assistant Framework

*Build production-ready AI assistants with pluggable architecture for LLM providers, communication channels, and custom
tools*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg?logo=spring)](https://spring.io/projects/spring-boot)
[![release](https://github.com/wutsi/kokibot/actions/workflows/release.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/release.yml)
[![master](https://github.com/wutsi/kokibot/actions/workflows/master.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/master.yml)
[![pr](https://github.com/wutsi/kokibot/actions/workflows/pr.yml/badge.svg)](https://github.com/wutsi/kokibot/actions/workflows/pr.yml)
[![JaCoCo Coverage](https://img.shields.io/badge/Coverage-93%25-brightgreen.svg)](target/site/jacoco/index.html)
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

### Step 1: Install Kokibot (macOS & Linux)

Run the following command in your terminal:

```bash
curl -fsSL https://github.com/wutsi/kokibot/releases/latest/download/install.sh | bash
```

This will automatically download and install Kokibot as a background service using the appropriate method for your
platform (`launchd` for macOS, `systemd` for Linux).

### Step 2: Set Environment Variables

When you install kokibot the first time, it runs with a default configuration that does not have any LLM or channel
configured.
So you need to set environment variables to configure the LLM and channel you want to use and restart the service.

#### Step 2.1: Configure the LLM

You must setup environment variables to enable the _brain_ of your AI assistant.

```bash
export KOKIBOT_LLM_TYPE="your-llm-api-key"      // Type of LLM: deepseek, kimi, gemini
export KOKIBOT_LLM_API_KEY="your-llm-api-key"   // LLM API Key
export KOKIBOT_LLM_MODEL="your-llm-model"       // LLM Model
```

#### Step 2.2: Configure the Channel

You must setup environment variables so that you can interact with your AI assistant.

```bash
export KOKIBOT_CHANNEL_TYPE="your-channel-type"   // Type of channel: telegram
export KOKIBOT_TOKEN="your-channel-token"         // Channel token: e.g., Telegram Bot Token
```

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

## Documentation

TODO

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ by the Kokibot team**

[⭐ Star us on GitHub](https://github.com/wutsi/kokibot) • [🐛 Report a Bug](https://github.com/wutsi/kokibot/issues) • [💡 Request a Feature](https://github.com/wutsi/kokibot/issues)

</div>
