# Runtime Assistant Settings Mutation

**Date:** 2026-06-24  
**Scope:** `assistant.*` settings only

## Goal

Allow the HTML configuration UI to update any `assistant.*` setting at runtime via a REST endpoint. Changes take effect immediately in the running process and are persisted to `settings.json`.

## Data Flow

```
POST /assistants/{name}/settings
Body: { "key": "max-iterations", "value": 15 }

AssistantController.set(name, key, value)
  → MultiBootstrap.bootstraps.find { name }
  → Bootstrap.set(key, value)
      1. Read settings.json fresh from disk (no env substitution)
      2. rawConfig["assistant"][key] = value
      3. Write rawConfig back to settings.json
      4. context.assistant.apply(key, value)  ← live update
```

Two responsibilities, cleanly separated:
- `Bootstrap.set()` owns persistence (read-modify-write on `settings.json`)
- `Assistant.apply()` owns the live field update

## Bootstrap.set()

Reads `settings.json` fresh on every call — no in-memory raw config state. This preserves manual edits and keeps `${ENV_VAR}` tokens in other sections intact.

```kotlin
fun set(key: String, value: Any) {
    val file = File(File(context.home, "config"), "settings.json")
    val rawConfig = JsonMapper().readValue(file, Map::class.java).toMutableMap()
    val assistantSection = (rawConfig.getOrPut("assistant") { mutableMapOf<String, Any>() })
        as MutableMap<String, Any>
    assistantSection[key] = value
    JsonMapper().writeValue(file, rawConfig)
    context.assistant.apply(key, value)
}
```

## Assistant.apply()

Dispatches on key, updates the in-memory field, and rebuilds stateful objects where needed.

| Key | Field updated | Side effect |
|-----|--------------|-------------|
| `max-iterations` | `maxIterations` | rebuild reasoning loop |
| `max-duration` | `maxDurationMinutes` | — |
| `thread-pool-size` | `threadPoolSize` | destroy + recreate `ToolOrchestrator`, rebuild reasoning loop |
| `description` | `description` | — |
| `coordinator` | `coordinator` | rebuild reasoning loop |
| unknown | — | throws `ConfigurationException` |

`rebuildReasoningLoop()` is a private helper that constructs a new `ReActReasoningLoop` from current field values, avoiding repeated constructor calls.

## REST Endpoint

```
POST /assistants/{name}/settings
{ "key": "max-iterations", "value": 15 }

200 OK  → { "success": true }
400     → { "error": "Unknown assistant setting: foo" }
404     → (assistant not found)
400     → (missing key or value in body)
```

Added to the existing `AssistantController`.

## Testing

| Test class | Coverage |
|-----------|----------|
| `AssistantApplyTest` | Each known key updates correct field; unknown key throws; `thread-pool-size` recreates orchestrator; `max-iterations`/`coordinator`/`thread-pool-size` rebuild reasoning loop |
| `BootstrapSetTest` | Reads file, merges assistant section, writes back, calls `apply()` |
| `AssistantControllerSetTest` | 200 on valid input; 400 on unknown key; 404 on missing assistant; 400 on missing body fields |
