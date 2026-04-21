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

#### brew

Kokibot uses Homebrew to manage dependencies and installation on macOS and Linux.
To install Homebrew, run the following command in your terminal:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### Java

Kokibot is built with Java, so you need to have it installed to run the service.
You can install Java using Homebrew:

```bash
brew install openjdk
```

**NOTE:** Kokibot requires Java 17 or higher.

#### Python

Some tools and integrations may require Python.
You can install Python using Homebrew:

```bash
brew install python
```

##### pipx

Kokibot uses `pipx` to manage Python dependencies for tools.
You can install `pipx` using Homebrew:

```bash
brew install pipx
pipx ensurepath
```

After installing `pipx`, you may need to restart your terminal or run `source ~/.bashrc` (or `source ~/.zshrc` or or
`source ~/.zprofile`) to update your PATH.

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
    - The binaries will be installed to `~/Application/kokikob`
    - The configuration, logs and data will be stored in `~/.kokibot`
- Run it a background service  (with `launchd` for macOS, `systemd` for Linux).

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
