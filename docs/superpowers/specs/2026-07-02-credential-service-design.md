# CredentialService Design

**Date:** 2026-07-02  
**Status:** Approved

## Problem

API keys and tokens are currently embedded directly in `settings.json` (or resolved from environment variables inline). This mixes secrets with configuration and makes it impossible to share a credential store across agents or manage credentials independently of agent settings.

## Goal

Introduce a `CredentialService` that is the single source of truth for all credentials (LLM API keys, channel tokens, MCP server tokens). No credential of any kind should appear in `settings.json` or any other config file.

---

## File Format

Two optional `.credential.json` files, one global and one per-agent:

```
~/kokibot/config/.credential.json                         ← global (shared across agents)
~/kokibot/agents/{agent}/config/.credential.json          ← agent-local (overrides global)
```

Both files use the same flat JSON format: a map of string keys to string values. `${ENV_VAR}` references are supported and resolved at load time via the existing `MapUtil.applyEnv()`.

```json
{
  "llm.deepseek":           "${DEEPSEEK_API_KEY}",
  "llm.gemini":             "AIza...",
  "llm.kimi":               "sk-...",
  "channel.telegram":       "${TELEGRAM_TOKEN}",
  "channel.email.password": "${EMAIL_PASSWORD}",
  "mcp.my-server":          "Bearer abc123"
}
```

### Key Naming Convention

| Pattern | Used for |
|---|---|
| `llm.{type}` | LLM API key (`deepseek`, `gemini`, `kimi`) |
| `channel.{type}` | Channel single-credential token (`telegram`) |
| `channel.{type}.{field}` | Channel multi-field credentials (`email.password`) |
| `mcp.{server-name}` | MCP server bearer token (matches `McpServerConfig.name`) |

### Resolution Rule

Agent-local file takes precedence. If a key exists in both files, the agent-local value wins. Missing file is not an error — treated as empty map.

---

## CredentialService Interface

**Location:** `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialService.kt`

```kotlin
enum class CredentialScope { LOCAL, GLOBAL }

interface CredentialService {
    fun get(key: String): String               // throws ConfigurationException if missing
    fun getOrNull(key: String): String?        // returns null if missing
    fun set(key: String, value: String, scope: CredentialScope = CredentialScope.LOCAL)
}
```

**Location:** `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImpl.kt`

- Holds two mutable maps: `globalCredentials` and `localCredentials`
- `getOrNull(key)` → `localCredentials[key] ?: globalCredentials[key]`
- `get(key)` → `getOrNull(key) ?: throw ConfigurationException("Credential '$key' not found in .credential.json")`
- `set(key, value, scope)` → writes to the in-memory map **and** persists to the corresponding `.credential.json` file on disk
- Malformed JSON at load time throws `ConfigurationException` (fail-fast)

---

## Loading

**In `Bootstrap.init()`**, before `context.init()` is called:

```
globalFile  = ~/kokibot/config/.credential.json
localFile   = ~/kokibot/agents/{agent}/config/.credential.json
```

`CredentialServiceImpl` is constructed from these two file paths, resolving `${ENV_VAR}` references immediately. The instance is passed into `Context` as a constructor field.

`Context` exposes it as:
```kotlin
val credentialService: CredentialService
```

Because `CredentialService` is initialized before `context.init()`, all resources (LLMs, channels, MCP) can use it during their own `init()`.

---

## Affected Components

Every component that previously read a credential from `config` is updated to call `context.credentialService` instead.

| Component | Credential key |
|---|---|
| `Deepseek.init()` | `credentialService.get("llm.deepseek")` |
| `Gemini.init()` | `credentialService.get("llm.gemini")` |
| `Kimi.init()` | `credentialService.get("llm.kimi")` |
| `TelegramChannel.init()` | `credentialService.get("channel.telegram")` |
| `EmailChannel.init()` | `credentialService.get("channel.email.password")` |
| `McpRegistry` (per server) | `credentialService.getOrNull("mcp.${server.name}")` |

The `api-key`, `token`, `password`, and similar fields are removed from `settings.json` and from any per-channel config files.

---

## Error Handling

- `get(key)` throws `ConfigurationException("Credential '$key' not found in .credential.json")` — consistent with current fail-fast pattern
- Missing `.credential.json` file → empty map, no error
- Malformed `.credential.json` → `ConfigurationException` at boot
- `set()` on a missing file → file is created automatically

---

## Testing

- **`CredentialServiceImplTest`** — unit tests covering:
  - `get()` / `getOrNull()` with key present in local, global, both, neither
  - Local overrides global
  - `${ENV_VAR}` resolution
  - Missing file treated as empty map
  - `set()` with LOCAL and GLOBAL scope (verifies in-memory state and file persistence)
  - Malformed JSON throws `ConfigurationException`
- Each affected LLM/channel test updated to supply a mock or stub `CredentialService` via `Context`

---

## Settings.json Changes

Before:
```json
{
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

After:
```json
{
  "llm": {
    "type": "deepseek",
    "model": "deepseek-chat"
  },
  "channels": [
    {
      "type": "telegram"
    }
  ]
}
```

All secrets move to `.credential.json`.
