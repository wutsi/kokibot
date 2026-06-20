# MCP Server over HTTPS — Design Spec

**Date:** 2026-06-20  
**Status:** Approved

---

## Overview

Add support for MCP (Model Context Protocol) servers over HTTPS to kokibot. Each MCP server is configured via a JSON file, exposed to the LLM through a dedicated activation tool (`mcp_activate`), and its tools are dynamically registered into the `ToolRegistry` upon activation — mirroring the existing skill activation pattern exactly.

---

## Goals

- Each MCP server has a name and description known to the LLM at all times
- The LLM activates an MCP server on demand via `mcp_activate`, identical in UX to `skill_activate`
- Activated MCP tools are added to the LLM's available tool list for the remainder of the request
- Transport: MCP Streamable HTTP (JSON-RPC 2.0 over HTTPS POST)
- Authentication: Bearer token (`Authorization: Bearer <token>`)

---

## Configuration

Each MCP server is configured via a JSON file at:

```
<HOME>/config/mcps/<mcp-name>.json
```

**Format:**

```json
{
  "name": "weather-mcp",
  "description": "Provides weather data and forecasts",
  "url": "https://weather-api.example.com/mcp",
  "token": "${WEATHER_MCP_TOKEN}"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier used in `mcp_activate` calls |
| `description` | Yes | Shown to the LLM so it knows when to activate |
| `url` | Yes | HTTPS endpoint of the MCP server |
| `token` | No | Bearer token; supports `${ENV_VAR}` substitution |

`McpRegistry` discovers all `*.json` files in this directory at startup. No network calls are made until activation.

---

## New Components

All classes reside in the `com.wutsi.kokibot.mcp` package:

```
src/main/kotlin/com/wutsi/kokibot/mcp/
├── McpServerConfig.kt      — data class: name, description, url, token
├── McpToolDefinition.kt    — data class: tool def from tools/list response
├── McpClient.kt            — HTTP client: initialize, listTools, callTool
├── McpServer.kt            — manages one server: config + client + activated flag
├── McpActivationTool.kt    — Tool impl: "mcp_activate" LLM-callable tool
├── McpTool.kt              — Tool impl: wraps one remote MCP tool
├── McpRegistry.kt          — discovers *.json, creates McpServer instances
└── McpCommand.kt           — /mcp command: lists servers + activation status
```

### McpServerConfig

```kotlin
data class McpServerConfig(
    val name: String,
    val description: String,
    val url: String,
    val token: String? = null
)
```

### McpToolDefinition

Represents one tool returned by the MCP server's `tools/list` response:

```kotlin
data class McpToolDefinition(
    val name: String,
    val description: String?,
    val inputSchema: Map<String, Any>
)
```

### McpClient

Uses OkHttp (existing dependency). Sends JSON-RPC 2.0 POSTs to the server URL.

**Headers on every request:**
- `Content-Type: application/json`
- `Authorization: Bearer <token>` (when token is configured)
- `Mcp-Session-Id: <id>` (after initialization)

**Operations:**
- `initialize()` — sends `initialize` method, stores `Mcp-Session-Id` from response header
- `listTools(): List<McpToolDefinition>` — sends `tools/list` method
- `callTool(name: String, arguments: Map<*, *>): String` — sends `tools/call`, extracts text content from result

**Session expiry:** If `callTool` receives a 4xx response indicating an invalid session, `McpClient` re-initializes once and retries.

### McpServer

Owns one `McpServerConfig` and one `McpClient`. Manages the activated state.

```kotlin
class McpServer(val config: McpServerConfig) {
    var activated: Boolean = false
    lateinit var client: McpClient

    fun activate(toolRegistry: ToolRegistry) { ... }
}
```

`activate()`:
1. Creates `McpClient(config.url, config.token)`
2. Calls `client.initialize()`
3. Calls `client.listTools()`
4. For each tool definition, creates `McpTool` and registers in `ToolRegistry`
5. Sets `activated = true`

A second call to `activate()` when already activated is a no-op (tools are already registered). Re-activation after session expiry is handled transparently by `McpClient` at call time, not by re-running `activate()`.

### McpRegistry

Parallel to `SkillRegistry`. Reads `<HOME>/config/mcps/*.json` on `init()`.

```kotlin
class McpRegistry {
    fun init(context: Context)        // discovers and creates McpServer instances
    fun all(): List<McpServer>
    fun get(name: String): McpServer  // throws McpNotFoundException if absent
    fun destroy()
}
```

### McpActivationTool

LLM-callable tool named `mcp_activate`. Parameter: `server` (String, required).

On execution:
1. Looks up server via `McpRegistry.get(server)`
2. Calls `McpServer.activate(context.toolRegistry)`
3. Returns: `"Activated <name>. Tools available: tool1, tool2, ..."`

On any error: returns the error message as a string (never throws), consistent with `SkillActivationTool`.

### McpTool

Wraps one `McpToolDefinition` as a kokibot `Tool`.

- `metadata()` — built from `McpToolDefinition` (name, description, parameters derived from `inputSchema`)
- `activate()` — returns `mcpRegistry.get(serverName).activated`
- `exec(arguments)` — calls `mcpRegistry.get(serverName).client.callTool(name, arguments)`

### McpCommand

`/mcp` command. Lists all configured MCP servers with their activation status. Parallel to `SkillCommand`.

---

## Context Changes

**`Context.kt`** — add one field:

```kotlin
val mcpRegistry: McpRegistry = McpRegistry()
```

**`Context.init()`** — add `initMcps()` called after `initMarketplaces()` and before `initTools()`:

```kotlin
private fun initMcps() {
    mcpRegistry.init(this)
}
```

**`Context.resources()`** — include `mcpRegistry.all()`.

**`ContextFactory.discoverTools()`** — add `McpActivationTool()`.

**`ContextFactory.discoverCommands()`** — add `McpCommand()`.

---

## Prompt Changes

**`PromptBuilder.buildSystemInstructions()`** — add `mcpInstructions(context)` entry (parallel to `skillsInstructions`):

```
# Available MCP Servers

The following MCP servers can be activated using the `mcp_activate` tool:

## MCP Server: weather-mcp
**Description:** Provides weather data and forecasts
```

This ensures the LLM knows which MCP servers exist and can decide when to activate them, even before any tool call is made.

---

## Activation & Request Flow

```
1. Startup
   McpRegistry.init() → reads <HOME>/config/mcps/*.json
                      → creates McpServer instances (no network)
                      → McpActivationTool registered in ToolRegistry

2. Each request — PromptBuilder.buildSystemInstructions()
   → mcpInstructions() lists servers by name + description
   → LLM sees: "MCP servers available: weather-mcp (weather data...)"

3. LLM calls: mcp_activate(server="weather-mcp")

4. McpActivationTool.exec()
   → McpRegistry.get("weather-mcp") → McpServer
   → McpServer.activate(toolRegistry):
       a. McpClient.initialize() — POST, gets Mcp-Session-Id
       b. McpClient.listTools()  — POST, returns tool definitions
       c. For each tool → McpTool registered in ToolRegistry
       d. activated = true
   → Returns "Activated weather-mcp. Tools available: get_weather, get_forecast"

5. Next LLM iteration — McpTool.activate() returns true
   → tools list now includes get_weather, get_forecast

6. LLM calls: get_weather(city="Seattle")

7. McpTool.exec()
   → mcpRegistry.get("weather-mcp").client.callTool("get_weather", args)
   → POST with Mcp-Session-Id header
   → Returns text content to LLM
```

---

## MCP Protocol (Streamable HTTP)

All requests POST to the configured server URL using JSON-RPC 2.0.

**Initialize:**
```json
{
  "jsonrpc": "2.0", "id": 1, "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {"tools": {}},
    "clientInfo": {"name": "kokibot", "version": "1.0"}
  }
}
```
Response header: `Mcp-Session-Id: <session-id>`

**List tools:**
```json
{"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
```

**Call tool:**
```json
{
  "jsonrpc": "2.0", "id": 3, "method": "tools/call",
  "params": {"name": "get_weather", "arguments": {"city": "Seattle"}}
}
```

**Tool call response:**
```json
{
  "jsonrpc": "2.0", "id": 3,
  "result": {
    "content": [{"type": "text", "text": "65°F, partly cloudy"}],
    "isError": false
  }
}
```

`McpClient` extracts text from all `content` items of type `text` and joins them.

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Server unreachable during `mcp_activate` | Returns error string to LLM; no exception |
| `tools/list` returns empty list | Returns "No tools available on this server" |
| `tools/call` fails | `McpTool.exec()` returns error string; consistent with all other tools |
| MCP session expired mid-request | `McpClient` re-initializes once and retries; returns error string if retry fails |
| Config file missing required fields | `McpRegistry.init()` logs warning and skips that file |

---

## Testing

**Unit tests** (one per class, Mockito):

| Test class | Coverage |
|------------|----------|
| `McpClientTest` | JSON-RPC payloads, session ID header, retry on expiry |
| `McpServerTest` | Activation registers tools, activated flag, no-op on second activate |
| `McpActivationToolTest` | Success/error return strings (mirrors `SkillActivationToolTest`) |
| `McpToolTest` | `activate()` delegates to server, `exec()` delegates to client |
| `McpRegistryTest` | File discovery, env var substitution, missing directory tolerance |
| `McpCommandTest` | `/mcp` output format |

No integration test against a real MCP server in v1 — `McpClient` is thin enough that mocked HTTP covers the contract.

---

## Out of Scope (v1)

- SSE transport
- OAuth 2.0 / client credentials auth
- MCP resources and prompts (tools only)
- Per-request session isolation (sessions are shared within a server instance)
