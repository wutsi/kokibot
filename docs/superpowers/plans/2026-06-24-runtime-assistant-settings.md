# Runtime Assistant Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose `Bootstrap.set(key, value)` to update any `assistant.*` setting at runtime — persisting to `settings.json` and applying live to the running `Assistant`.

**Architecture:** `Bootstrap.set()` does a read-modify-write on `settings.json`, then calls `Assistant.apply()` which dispatches on the key and updates the appropriate in-memory field (rebuilding `ReasoningLoop`/`ToolOrchestrator` where needed). A new `POST /assistants/{name}/settings` endpoint in `AssistantController` exposes this to callers.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5, `com.nhaarman.mockitokotlin2`

## Global Constraints

- Run `mvn antrun:run@ktlint-format` before every commit
- Line coverage ≥ 90%, class coverage ≥ 90% (JaCoCo)
- Use `mock<Type>()` and `doReturn(...).whenever(...)` from `com.nhaarman.mockitokotlin2`
- No features beyond what is described in this plan

---

### Task 1: `Assistant.apply()` with tests

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/AssistantApplyTest.kt`

**Interfaces:**
- Produces: `fun apply(key: String, value: Any)` — public method on `Assistant`
- Produces: `internal var maxIterations`, `maxDurationMinutes`, `threadPoolSize`, `toolOrchestrator`, `reasoningLoop` — readable in tests

---

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/wutsi/kokibot/AssistantApplyTest.kt`:

```kotlin
package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class AssistantApplyTest {
    private lateinit var assistant: Assistant

    @BeforeEach
    fun setup() {
        val context = Context(
            home = File("target/test-data/assistant-apply"),
            llm = mock<LLM>(),
            toolRegistry = mock<ToolRegistry>(),
            memory = mock<Memory>(),
            skillRegistry = mock<SkillRegistry>(),
            dailyLog = mock<DailyLog>(),
            sessionLog = mock<SessionLog>(),
            chatHistory = mock<ChatHistory>(),
            assistantRegistry = AssistantRegistry(),
        )
        assistant = Assistant("test")
        assistant.init(
            mapOf(
                "max-iterations" to 5,
                "description" to "original",
                "coordinator" to false,
                "thread-pool-size" to 4,
            ),
            context,
        )
    }

    @Test
    fun `apply max-iterations updates field and rebuilds loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("max-iterations", 20)
        assertEquals(20, assistant.maxIterations)
        assertNotSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply max-duration updates field`() {
        assistant.apply("max-duration", "10m")
        assertEquals(10L, assistant.maxDurationMinutes)
    }

    @Test
    fun `apply description updates field without rebuilding loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("description", "new description")
        assertEquals("new description", assistant.description)
        assertSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply coordinator updates field and rebuilds loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("coordinator", true)
        assertEquals(true, assistant.coordinator)
        assertNotSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply thread-pool-size updates field, replaces orchestrator, and rebuilds loop`() {
        val originalOrchestrator = assistant.toolOrchestrator
        val originalLoop = assistant.reasoningLoop
        assistant.apply("thread-pool-size", 6)
        assertEquals(6, assistant.threadPoolSize)
        assertNotSame(originalOrchestrator, assistant.toolOrchestrator)
        assertNotSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply thread-pool-size coerces minimum to 2`() {
        assistant.apply("thread-pool-size", 1)
        assertEquals(2, assistant.threadPoolSize)
    }

    @Test
    fun `apply unknown key throws ConfigurationException`() {
        assertThrows<ConfigurationException> {
            assistant.apply("unknown-key", "value")
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AssistantApplyTest
```

Expected: FAIL — `apply`, `maxIterations`, `maxDurationMinutes`, `threadPoolSize`, `toolOrchestrator`, `reasoningLoop` do not exist yet.

- [ ] **Step 3: Make `Assistant` fields internal and add `apply()` + `rebuildReasoningLoop()`**

In `src/main/kotlin/com/wutsi/kokibot/Assistant.kt`, change the five `private` field declarations and add the two new methods:

```kotlin
// Change from private to internal (4 fields):
internal var maxIterations: Int = DEFAULT_ITERATIONS
internal var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
internal var threadPoolSize: Int = 4
internal lateinit var toolOrchestrator: ToolOrchestrator
internal lateinit var reasoningLoop: ReasoningLoop
```

Add after `destroy()`:

```kotlin
fun apply(key: String, value: Any) {
    when (key) {
        "max-iterations" -> {
            maxIterations = value.toString().toInt()
            rebuildReasoningLoop()
        }
        "max-duration" -> {
            maxDurationMinutes = DurationUtil.minutes(value.toString(), DEFAULT_MAX_DURATION_MINUTES)
        }
        "thread-pool-size" -> {
            threadPoolSize = value.toString().toInt().coerceAtLeast(2)
            toolOrchestrator.destroy()
            toolOrchestrator = ToolOrchestrator(threadPoolSize = threadPoolSize)
            rebuildReasoningLoop()
        }
        "description" -> description = value.toString()
        "coordinator" -> {
            coordinator = value.toString().toBoolean()
            rebuildReasoningLoop()
        }
        else -> throw ConfigurationException("Unknown assistant setting: $key")
    }
}

private fun rebuildReasoningLoop() {
    reasoningLoop = ReActReasoningLoop(
        assistantName = name,
        maxIterations = maxIterations,
        coordinator = coordinator,
        promptBuilder = promptBuilder,
        toolOrchestrator = toolOrchestrator,
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=AssistantApplyTest
```

Expected: all 7 tests PASS.

- [ ] **Step 5: Run the full test suite to check for regressions**

```bash
mvn test
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 6: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt \
        src/test/kotlin/com/wutsi/kokibot/AssistantApplyTest.kt
git commit -m "$(cat <<'EOF'
feat: add Assistant.apply() for runtime setting mutation

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `Bootstrap.set()` with tests

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/BootstrapSetTest.kt`

**Interfaces:**
- Consumes: `Assistant.apply(key: String, value: Any)` from Task 1
- Produces: `fun set(key: String, value: Any)` — public method on `Bootstrap`

---

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/wutsi/kokibot/BootstrapSetTest.kt`:

```kotlin
package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class BootstrapSetTest {
    private val contextFactory = mock<ContextFactory>()
    private val bootstrap = Bootstrap(contextFactory)
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()

    private val tempDir = File("target/test-data/bootstrap-set")

    @BeforeEach
    fun setup() {
        tempDir.deleteRecursively()
        val configDir = File(tempDir, "config")
        configDir.mkdirs()
        File(configDir, "settings.json").writeText(
            """{"assistant":{"max-iterations":10},"llm":{"type":"deepseek"}}"""
        )

        doReturn(tempDir).whenever(context).home
        doReturn(assistant).whenever(context).assistant
        doReturn(Health(id = "-")).whenever(context).health()
        doReturn(context).whenever(contextFactory).create(any(), any())

        bootstrap.init(getResourceFile("/home/007"))
    }

    @Test
    fun `set updates assistant section in settings json on disk`() {
        bootstrap.set("max-iterations", 20)

        val raw = JsonMapper().readValue(File(tempDir, "config/settings.json"), Map::class.java)
        val section = raw["assistant"] as Map<*, *>
        assertEquals(20, section["max-iterations"])
    }

    @Test
    fun `set preserves other sections in settings json`() {
        bootstrap.set("description", "hello")

        val raw = JsonMapper().readValue(File(tempDir, "config/settings.json"), Map::class.java)
        val llm = raw["llm"] as Map<*, *>
        assertEquals("deepseek", llm["type"])
    }

    @Test
    fun `set calls assistant apply with key and value`() {
        bootstrap.set("description", "hello")

        verify(assistant).apply("description", "hello")
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapSetTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return File(resource.toURI())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=BootstrapSetTest
```

Expected: FAIL — `Bootstrap.set` does not exist yet.

- [ ] **Step 3: Add `set()` to `Bootstrap`**

In `src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt`, add after `getContext()`:

```kotlin
fun set(key: String, value: Any) {
    val file = File(File(context.home, "config"), "settings.json")
    @Suppress("UNCHECKED_CAST")
    val rawConfig = JsonMapper().readValue(file, Map::class.java).toMutableMap() as MutableMap<Any?, Any?>
    @Suppress("UNCHECKED_CAST")
    val assistantSection = rawConfig.getOrPut("assistant") { mutableMapOf<String, Any>() } as MutableMap<Any?, Any?>
    assistantSection[key] = value
    JsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, rawConfig)
    context.assistant.apply(key, value)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=BootstrapSetTest
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Run the full test suite to check for regressions**

```bash
mvn test
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 6: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt \
        src/test/kotlin/com/wutsi/kokibot/BootstrapSetTest.kt
git commit -m "$(cat <<'EOF'
feat: add Bootstrap.set() to persist and apply assistant settings at runtime

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: REST endpoint `POST /assistants/{name}/settings`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/controller/AssistantControllerTest.kt`

**Interfaces:**
- Consumes: `Bootstrap.set(key: String, value: Any)` from Task 2
- Produces: `POST /assistants/{name}/settings` — `{"key":"…","value":…}` → `{"success":true}` or `{"error":"…"}`

---

- [ ] **Step 1: Write the failing tests**

Add the following four test methods inside the `AssistantControllerTest` class in `src/test/kotlin/com/wutsi/kokibot/controller/AssistantControllerTest.kt`.

First, add these imports at the top of the file (if not already present):

```kotlin
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.ConfigurationException
```

Then add the test methods:

```kotlin
@Test
fun `set - success`() {
    val bootstrap = mock(Bootstrap::class.java)
    val assistant = mock<Assistant>()
    doReturn("007").whenever(assistant).name
    val context = Context(
        assistant = assistant,
        home = File("target/assistant-controller/007"),
        llm = mock<LLM>(),
    )
    doReturn(context).whenever(bootstrap).getContext()
    doReturn(listOf(bootstrap)).whenever(multi).bootstraps

    val response = rest.postForEntity(
        "/assistants/007/settings",
        mapOf("key" to "description", "value" to "hello"),
        Map::class.java,
    )

    assertEquals(200, response.statusCode.value())
    assertEquals(true, response.body!!["success"])
    verify(bootstrap).set("description", "hello")
}

@Test
fun `set - not found when assistant name unknown`() {
    doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

    val response = rest.postForEntity(
        "/assistants/xxx/settings",
        mapOf("key" to "description", "value" to "hello"),
        Map::class.java,
    )

    assertEquals(404, response.statusCode.value())
}

@Test
fun `set - bad request when key missing from body`() {
    doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

    val response = rest.postForEntity(
        "/assistants/007/settings",
        mapOf("value" to "hello"),
        Map::class.java,
    )

    assertEquals(400, response.statusCode.value())
}

@Test
fun `set - bad request when unknown key`() {
    val bootstrap = mock(Bootstrap::class.java)
    val assistant = mock<Assistant>()
    doReturn("007").whenever(assistant).name
    val context = Context(
        assistant = assistant,
        home = File("target/assistant-controller/007"),
        llm = mock<LLM>(),
    )
    doReturn(context).whenever(bootstrap).getContext()
    doThrow(ConfigurationException("Unknown assistant setting: invalid")).whenever(bootstrap).set(any(), any())
    doReturn(listOf(bootstrap)).whenever(multi).bootstraps

    val response = rest.postForEntity(
        "/assistants/007/settings",
        mapOf("key" to "invalid", "value" to "hello"),
        Map::class.java,
    )

    assertEquals(400, response.statusCode.value())
    assertEquals("Unknown assistant setting: invalid", (response.body as Map<*, *>)["error"])
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AssistantControllerTest
```

Expected: the 4 new tests FAIL — the endpoint does not exist yet.

- [ ] **Step 3: Add the endpoint to `AssistantController`**

In `src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt`, add after the existing `@PostMapping("/{name}/heartbeat.md")` method:

First add these imports if not present:
```kotlin
import com.wutsi.kokibot.ConfigurationException
```

Then add the method:

```kotlin
@PostMapping("/{name}/settings")
fun set(
    @PathVariable name: String,
    @RequestBody body: Map<String, Any>,
): ResponseEntity<Map<String, Any>> {
    val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
    val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
    val value = body["value"] ?: return ResponseEntity.badRequest().build()
    return try {
        bootstrap.set(key, value)
        ResponseEntity.ok(mapOf("success" to true))
    } catch (e: ConfigurationException) {
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=AssistantControllerTest
```

Expected: all new tests PASS plus no regressions in the existing controller tests.

- [ ] **Step 5: Run the full test suite**

```bash
mvn test
```

Expected: BUILD SUCCESS, no failures, coverage ≥ 90%.

- [ ] **Step 6: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt \
        src/test/kotlin/com/wutsi/kokibot/controller/AssistantControllerTest.kt
git commit -m "$(cat <<'EOF'
feat: expose POST /assistants/{name}/settings for runtime configuration

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```
