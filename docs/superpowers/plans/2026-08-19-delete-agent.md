# Delete Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an operator delete a registered agent via a REST endpoint, safely (soft-delete: unregister, stop, and move its home directory to a trash folder instead of removing it permanently).

**Architecture:** Mirrors the existing `rename` feature exactly. `MultiBootstrap.delete(name)` finds the agent's `Bootstrap`, removes it from the in-memory list, calls `bootstrap.destroy()` (which tears down its `Context` and unregisters it from `AssistantRegistry` via `Assistant.destroy()`), then moves the agent's home directory to `{agentsDir}/.trash/{name}-{timestamp}/` using `Files.move`. `AssistantController` exposes this as `DELETE /assistants/{name}`.

**Tech Stack:** Kotlin, Spring Boot (`@RestController`), JUnit 5, `com.nhaarman.mockitokotlin2`.

**Spec:** No separate spec file — design was agreed in chat (bounded change, soft-delete via move-to-trash, REST-only trigger, no restore/purge tooling in scope).

## Global Constraints

- Deletion is **soft**: never call `File.deleteRecursively()` on an agent's home directory. Always move it into a `.trash/` subdirectory of the agents root, using `Files.move` (same primitive `rename()` uses).
- Trash directory naming: `{name}-{epochMillis}` to avoid collisions if the same name is deleted twice.
- No `/delete` chat command and no restore/purge endpoint — REST delete only, out of scope per the approved design.
- Follow existing code style exactly: same exception types (`AssistantNotFoundException`), same test patterns (`MultiBootstrapTest`, `AssistantControllerTest`).
- `MultiBootstrap` and `Bootstrap` currently have no injectable clock — timestamps must come from `System.currentTimeMillis()` directly at the call site (no new abstraction needed for this scope).

---

### Task 1: `MultiBootstrap.delete()`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/MultiBootstrap.kt`
- Test: `src/test/kotlin/com/wutsi/kokibot/MultiBootstrapTest.kt`

**Interfaces:**
- Consumes: `Bootstrap.destroy()` (existing, no signature change), `Bootstrap.getContext(): Context` (existing), `AssistantNotFoundException` (existing, in `com.wutsi.kokibot` package).
- Produces: `MultiBootstrap.delete(name: String): Unit` — throws `AssistantNotFoundException` if `name` is not a currently-registered agent. On success, the agent is removed from `bootstraps`, its `Context` is destroyed, and its home directory no longer exists at its original path (moved under `.trash/`).

- [ ] **Step 1: Write the failing tests**

Add to `MultiBootstrapTest.kt` (uses the same `tempHome` pattern as the `rename` tests — copy the `/home/multi-agent` test fixture into a temp dir per test so we never mutate the checked-in fixture):

```kotlin
    @Test
    fun delete() {
        tempHome = Files.createTempDirectory("kokibot-delete-test").toFile()
        home.copyRecursively(tempHome!!)

        bootstrap.init(tempHome!!)
        assertEquals(2, bootstrap.bootstraps.size)

        bootstrap.delete("007")

        assertEquals(1, bootstrap.bootstraps.size)
        assertThrows<AssistantNotFoundException> { assistantRegistry.get("007") }
        assertFalse(File(tempHome, "agents/007").exists())

        val trashed = File(tempHome, "agents/.trash").listFiles()
        assertNotNull(trashed)
        assertEquals(1, trashed!!.size)
        assertTrue(trashed[0].name.startsWith("007-"))
        assertTrue(File(trashed[0], "config/settings.json").exists())
    }

    @Test
    fun `delete - unknown name throws`() {
        bootstrap.init(home)

        assertThrows<AssistantNotFoundException> {
            bootstrap.delete("unknown")
        }
        assertEquals(2, bootstrap.bootstraps.size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=MultiBootstrapTest#delete+delete_-_unknown_name_throws`
Expected: FAIL with a compile error — `delete` is unresolved on `MultiBootstrap`.

- [ ] **Step 3: Implement `MultiBootstrap.delete()`**

Add to `MultiBootstrap.kt`, right after the existing `rename()` method (around line 81):

```kotlin
    fun delete(name: String) {
        val bootstrap = get(name) ?: throw AssistantNotFoundException("Assistant with name `$name` not found")
        val home = bootstrap.getContext().home

        bootstraps.remove(bootstrap)
        bootstrap.destroy()

        val trashDir = File(home.parentFile, ".trash")
        trashDir.mkdirs()
        Files.move(home.toPath(), File(trashDir, "$name-${System.currentTimeMillis()}").toPath())
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=MultiBootstrapTest`
Expected: PASS (all tests, including the two new ones and the pre-existing `rename`/`init`/`destroy` tests).

- [ ] **Step 5: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/MultiBootstrap.kt src/test/kotlin/com/wutsi/kokibot/MultiBootstrapTest.kt
git commit -m "feat: add MultiBootstrap.delete() to soft-delete an agent"
```

---

### Task 2: `DELETE /assistants/{name}` endpoint

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt`
- Test: `src/test/kotlin/com/wutsi/kokibot/controller/AssistantControllerTest.kt`

**Interfaces:**
- Consumes: `MultiBootstrap.delete(name: String)` from Task 1, `MultiBootstrap.bootstraps: List<Bootstrap>` (existing), `AssistantNotFoundException` (existing).
- Produces: `AssistantController.delete(name: String): ResponseEntity<Map<String, Any>>` mapped to `DELETE /assistants/{name}` — returns `200 {"success": true}` on success, `404` if the agent does not exist.

- [ ] **Step 1: Write the failing tests**

Add to `AssistantControllerTest.kt`, near the other `set` tests:

```kotlin
    @Test
    fun `delete - success`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.exchange(
            "/assistants/007",
            org.springframework.http.HttpMethod.DELETE,
            null,
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(multi).delete("007")
    }

    @Test
    fun `delete - not found when assistant name unknown`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps
        doThrow(AssistantNotFoundException("Assistant not found: xxx")).whenever(multi).delete("xxx")

        val response = rest.exchange(
            "/assistants/xxx",
            org.springframework.http.HttpMethod.DELETE,
            null,
            Any::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }
```

Add the missing import to the test file's import block:

```kotlin
import com.wutsi.kokibot.AssistantNotFoundException
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AssistantControllerTest#delete_-_success+delete_-_not_found_when_assistant_name_unknown`
Expected: FAIL — no `DELETE /assistants/{name}` mapping exists yet, so the first test gets a `405`/non-200 status instead of `200`.

- [ ] **Step 3: Implement the endpoint**

Add to `AssistantController.kt`, right after the `set()` method (around line 134), and add the `DeleteMapping` and `AssistantNotFoundException` imports:

```kotlin
import com.wutsi.kokibot.AssistantNotFoundException
```
```kotlin
import org.springframework.web.bind.annotation.DeleteMapping
```

```kotlin
    @DeleteMapping("/{name}")
    fun delete(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        return try {
            multi.delete(name)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: AssistantNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AssistantControllerTest`
Expected: PASS (all tests, including the two new ones).

- [ ] **Step 5: Format, full build, and commit**

```bash
mvn antrun:run@ktlint-format
mvn clean install
git add src/main/kotlin/com/wutsi/kokibot/controller/AssistantController.kt src/test/kotlin/com/wutsi/kokibot/controller/AssistantControllerTest.kt
git commit -m "feat: add DELETE /assistants/{name} endpoint to soft-delete an agent"
```
