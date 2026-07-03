# CredentialService Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize all API keys and tokens in `.credential.json` files, removing them from `settings.json`, by
introducing a `CredentialService` accessible via `Context`.

**Architecture:** `CredentialServiceImpl` loads a global `~/kokibot/config/.credential.json` and an agent-local
`~/kokibot/agents/{agent}/config/.credential.json` at boot; local values override global. `Bootstrap.init()` creates the
service before `context.init()` so all LLM/channel/MCP `init()` calls can read credentials via
`context.credentialService`.

**Tech Stack:** Kotlin 2.3, JUnit 5, Mockito Kotlin (`com.nhaarman.mockitokotlin2`), Jackson `JsonMapper`.

## Global Constraints

- Run `mvn antrun:run@ktlint-format` before every commit.
- Line coverage ≥ 90%, class coverage ≥ 90% (jacoco).
- Test pattern: `mock<Type>()` and `whenever(...).doReturn(...)` (mockito-kotlin).
- All new files go under `com.wutsi.kokibot.*` package matching directory structure.
- No `api-key`, `token`, or `password` fields may remain in any `settings.json` or MCP JSON config.

---

## File Map

**Create:**

- `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialService.kt` — interface + `CredentialScope` enum
- `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImpl.kt` — file-backed implementation
- `src/main/kotlin/com/wutsi/kokibot/service/credential/NoOpCredentialService.kt` — null-object default for Context
- `src/test/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImplTest.kt`

**Modify:**

- `src/main/kotlin/com/wutsi/kokibot/Context.kt` — add `credentialService: CredentialService = NoOpCredentialService`
  field
- `src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt` — create `CredentialServiceImpl` before `context.init()`
- `src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt` — accept `credentialService` parameter, pass to `Context`
- `src/main/kotlin/com/wutsi/kokibot/llm/deepseek/Deepseek.kt` — use `context.credentialService.get("llm.${name()}")` (
  covers Gemini + Kimi via inheritance)
- `src/main/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannel.kt` — use
  `context.credentialService.get("channel.telegram")`
- `src/main/kotlin/com/wutsi/kokibot/channel/email/EmailChannel.kt` — use
  `context.credentialService.get("channel.email.password")`
- `src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt` — use `context.credentialService.getOrNull("mcp.${name}")`
- `src/test/kotlin/com/wutsi/kokibot/llm/deepseek/DeepseekTest.kt` — inject mock `CredentialService` into `Context`
- `src/test/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannelTest.kt` — inject mock `CredentialService`
- `src/test/kotlin/com/wutsi/kokibot/channel/email/EmailChannelTest.kt` — inject mock `CredentialService`, remove
  `password` from config
- `src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt` — pass real `Context` with mock `CredentialService`

---

## Task 1: CredentialService — Interface, Implementation, Tests

**Files:**

- Create: `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialService.kt`
- Create: `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImpl.kt`
- Create: `src/main/kotlin/com/wutsi/kokibot/service/credential/NoOpCredentialService.kt`
- Test: `src/test/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImplTest.kt`

**Interfaces:**

- Produces: `CredentialService.get(key: String): String`, `CredentialService.getOrNull(key: String): String?`,
  `CredentialService.set(key: String, value: String, scope: CredentialScope)`, `NoOpCredentialService` object,
  `CredentialServiceImpl(globalFile: File, localFile: File)`

---

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImplTest.kt`:

```kotlin
package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialServiceImplTest {
    @TempDir
    lateinit var tempDir: File

    private fun globalFile() = File(tempDir, "global.json")
    private fun localFile() = File(tempDir, "local.json")

    @Test
    fun `getOrNull returns null when both files missing`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertNull(svc.getOrNull("llm.deepseek"))
    }

    @Test
    fun `get throws ConfigurationException when key missing`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertThrows<ConfigurationException> { svc.get("llm.deepseek") }
    }

    @Test
    fun `get returns global value`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("global-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `get returns local value`() {
        localFile().writeText("""{"llm.deepseek":"local-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("local-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `local overrides global`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        localFile().writeText("""{"llm.deepseek":"local-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("local-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `get falls back to global when key only in global`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        localFile().writeText("""{"channel.telegram":"local-token"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("global-key", svc.get("llm.deepseek"))
        assertEquals("local-token", svc.get("channel.telegram"))
    }

    @Test
    fun `malformed JSON throws ConfigurationException`() {
        globalFile().writeText("not-json")
        assertThrows<ConfigurationException> { CredentialServiceImpl(globalFile(), localFile()) }
    }

    @Test
    fun `set LOCAL writes to local map and persists file`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("channel.telegram", "new-token", CredentialScope.LOCAL)
        assertEquals("new-token", svc.getOrNull("channel.telegram"))
        assertTrue(localFile().exists())
    }

    @Test
    fun `set GLOBAL writes to global map and persists file`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.deepseek", "new-key", CredentialScope.GLOBAL)
        assertEquals("new-key", svc.getOrNull("llm.deepseek"))
        assertTrue(globalFile().exists())
    }

    @Test
    fun `set default scope is LOCAL`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.kimi", "kimi-key")
        assertEquals("kimi-key", svc.getOrNull("llm.kimi"))
        assertTrue(localFile().exists())
    }

    @Test
    fun `set persisted value survives reload`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.deepseek", "persisted-key", CredentialScope.LOCAL)

        val svc2 = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("persisted-key", svc2.get("llm.deepseek"))
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
mvn test -Dtest=CredentialServiceImplTest 2>&1 | tail -20
```

Expected: compilation error (`CredentialServiceImpl` not found).

- [ ] **Step 3: Create the interface**

Create `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialService.kt`:

```kotlin
package com.wutsi.kokibot.service.credential

enum class CredentialScope { LOCAL, GLOBAL }

interface CredentialService {
    fun get(key: String): String
    fun getOrNull(key: String): String?
    fun set(key: String, value: String, scope: CredentialScope = CredentialScope.LOCAL)
}
```

- [ ] **Step 4: Create the implementation**

Create `src/main/kotlin/com/wutsi/kokibot/service/credential/CredentialServiceImpl.kt`:

```kotlin
package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.util.MapUtil
import tools.jackson.databind.json.JsonMapper
import java.io.File

class CredentialServiceImpl(
    private val globalFile: File,
    private val localFile: File,
    private val jsonMapper: JsonMapper = JsonMapper(),
) : CredentialService {
    private val globalCredentials: MutableMap<String, String> = mutableMapOf()
    private val localCredentials: MutableMap<String, String> = mutableMapOf()

    init {
        load(globalFile, globalCredentials)
        load(localFile, localCredentials)
    }

    override fun getOrNull(key: String): String? =
        localCredentials[key] ?: globalCredentials[key]

    override fun get(key: String): String =
        getOrNull(key) ?: throw ConfigurationException("Credential '$key' not found in .credentials.json")

    override fun set(key: String, value: String, scope: CredentialScope) {
        val map = if (scope == CredentialScope.LOCAL) localCredentials else globalCredentials
        val file = if (scope == CredentialScope.LOCAL) localFile else globalFile
        map[key] = value
        persist(file, map)
    }

    private fun load(file: File, target: MutableMap<String, String>) {
        if (!file.exists()) return
        try {
            val raw = jsonMapper.readValue(file, Map::class.java)
            val resolved = MapUtil.applyEnv(raw)
            resolved.forEach { (k, v) -> if (k != null && v != null) target[k.toString()] = v.toString() }
        } catch (ex: Exception) {
            throw ConfigurationException("Failed to parse ${file.name}: ${ex.message}")
        }
    }

    private fun persist(file: File, map: Map<String, String>) {
        file.parentFile?.mkdirs()
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, map)
    }
}
```

- [ ] **Step 5: Create the no-op default**

Create `src/main/kotlin/com/wutsi/kokibot/service/credential/NoOpCredentialService.kt`:

```kotlin
package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException

object NoOpCredentialService : CredentialService {
    override fun get(key: String): String =
        throw ConfigurationException("Credential '$key' not found in .credentials.json")

    override fun getOrNull(key: String): String? = null

    override fun set(key: String, value: String, scope: CredentialScope) {}
}
```

- [ ] **Step 6: Run tests — expect green**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=CredentialServiceImplTest
```

Expected: `BUILD SUCCESS`, all 11 tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/service/credential/ \
        src/test/kotlin/com/wutsi/kokibot/service/credential/
git commit -m "feat: add CredentialService with local/global file-backed storage"
```

---

## Task 2: Wire CredentialService into Context and Bootstrap

**Files:**

- Modify: `src/main/kotlin/com/wutsi/kokibot/Context.kt`
- Modify: `src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt`
- Modify: `src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt`

**Interfaces:**

- Consumes: `CredentialService`, `CredentialServiceImpl(globalFile, localFile)`, `NoOpCredentialService` from Task 1
- Produces: `Context.credentialService: CredentialService` (accessible to all resources during `init()`)

---

- [ ] **Step 1: Add `credentialService` to `Context`**

In `src/main/kotlin/com/wutsi/kokibot/Context.kt`, add the field after the existing `val config` field:

```kotlin
val credentialService: CredentialService = NoOpCredentialService,
```

Add the import at the top of the file:

```kotlin
import com.wutsi.kokibot.service.credential.CredentialService
import com.wutsi.kokibot.service.credential.NoOpCredentialService
```

- [ ] **Step 2: Update `ContextFactory.create()` to accept and pass `credentialService`**

In `src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt`, change the `create` signature and body:

```kotlin
fun create(home: File, config: Map<*, *>, credentialService: CredentialService): Context {
    discoverTools().forEach { tool -> toolRegistry.register(tool) }
    discoverCommands().forEach { command -> commandRegistry.register(command) }

    return Context(
        home = home,
        llm = createLLM(config),
        assistant = Assistant(home.name),
        toolRegistry = toolRegistry,
        channelRegistry = channelRegistry,
        commandRegistry = commandRegistry,
        skillRegistry = skillRegistry,
        dailyLog = DailyLog(),
        memory = Memory(),
        config = config,
        jsonMapper = jsonMapper,
        assistantRegistry = assistantRegistry,
        credentialService = credentialService,
    )
}
```

Add the import:

```kotlin
import com.wutsi.kokibot.service.credential.CredentialService
```

- [ ] **Step 3: Update `Bootstrap.init()` to create `CredentialServiceImpl`**

In `src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt`, update `init()`:

```kotlin
fun init(home: File) {
    LOGGER.info("... Initializing Assistant: @${home.name} .............................................")

    val config = loadConfig(File(getConfigDir(home), "settings.json"))
    val credentialService = loadCredentialService(home)
    this.context = contextFactory.create(home, config, credentialService)

    context.init(config)

    LOGGER.info("Initialization completed")
}

private fun loadCredentialService(home: File): CredentialService {
    val globalFile = File(home.parentFile.parentFile, "config/.credentials.json")
    val localFile = File(getConfigDir(home), ".credentials.json")
    return CredentialServiceImpl(globalFile, localFile)
}
```

Add the imports:

```kotlin
import com.wutsi.kokibot.service.credential.CredentialService
import com.wutsi.kokibot.service.credential.CredentialServiceImpl
```

- [ ] **Step 4: Update `ContextFactoryTest` to pass `NoOpCredentialService`**

In `src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt`, add the import and update all four
`factory.create(home, config)` call sites:

```kotlin
import com.wutsi.kokibot.service.credential.NoOpCredentialService
```

Replace every occurrence of:

```kotlin
val context = factory.create(home, config)
```

With:

```kotlin
val context = factory.create(home, config, NoOpCredentialService)
```

Also add `credentialService` to the assertion in the `create` test:

```kotlin
assertEquals(NoOpCredentialService, context.credentialService)
```

- [ ] **Step 5: Run the full test suite**

```bash
mvn antrun:run@ktlint-format && mvn test
```

Expected: `BUILD SUCCESS`. All previously passing tests continue to pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Context.kt \
        src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt \
        src/main/kotlin/com/wutsi/kokibot/Bootstrap.kt \
        src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt
git commit -m "feat: wire CredentialService into Context and Bootstrap"
```

---

## Task 3: Update Deepseek (covers Gemini + Kimi via inheritance)

**Files:**

- Modify: `src/main/kotlin/com/wutsi/kokibot/llm/deepseek/Deepseek.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/llm/deepseek/DeepseekTest.kt`

**Interfaces:**

- Consumes: `Context.credentialService: CredentialService` from Task 2

**Why one change covers all three LLMs:** `Gemini` and `Kimi` extend `Deepseek` without overriding `init()`, and they
override `name()` to return `"gemini"` / `"kimi"`. Using `context.credentialService.get("llm.${name()}")` automatically
uses the right key for each subclass.

---

- [ ] **Step 1: Update `DeepseekTest` to inject mock `CredentialService`**

In `src/test/kotlin/com/wutsi/kokibot/llm/deepseek/DeepseekTest.kt`, replace the `config` and `context` setup:

```kotlin
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.service.credential.CredentialService
// ... existing imports ...

class DeepseekTest {
    private val llm = Deepseek()
    private val credentialService = mock<CredentialService>()
    private val config = mapOf(
        "model" to "deepseek-v4-flash",
        "thinking" to false,
        "max-tokens" to 2024,
        "temperature" to 0.7,
        "read-timeout-millis" to 30000,
        "connect-timeout-millis" to 10000,
        "tools" to listOf("date_tool_now", "web_tool_search", "web_tool_fetch"),
    )
    private val context = Context(
        home = File("/target"),
        llm = mock(),
        config = config,
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.deepseek")).doReturn(System.getenv("DEEPSEEK_API_KEY") ?: "")
    }
    // ... rest of tests unchanged ...
}
```

Also remove the inline `"api-key" to ...` entries from any config maps inside individual test methods (e.g.
`completion`, `balance`, `health - up`, `health - down`, `completion with streaming`). Replace them with a
`whenever(credentialService.get("llm.deepseek")).doReturn("ds-xxx")` in the test body where a specific key value is
needed.

For `health - down`, which tests a bad API key:

```kotlin
@Test
fun `health - down`() {
    whenever(credentialService.get("llm.deepseek")).doReturn("xxxxx")
    llm.init(config, context)
    val health = llm.health()
    assertEquals(llm.id(), health.id)
    assertEquals(false, health.up)
    assertNotNull(health.details)
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
mvn test -Dtest=DeepseekTest
```

Expected: `BUILD SUCCESS` (compiles), but `init` tests fail because `Deepseek.init()` still reads `config["api-key"]`
which is now missing.

- [ ] **Step 3: Update `Deepseek.init()` to use `credentialService`**

In `src/main/kotlin/com/wutsi/kokibot/llm/deepseek/Deepseek.kt`, replace the `init()` method:

```kotlin
override fun init(config: Map<*, *>, context: Context) {
    val apiKey = context.credentialService.get("llm.${name()}")
    val model = config["model"] as String? ?: throw ConfigurationException("model is required")

    this.context = context
    this.streamingEnabled = MapUtil.toBoolean("streaming", config) ?: false
    this.thinking = MapUtil.toBoolean("thinking", config) ?: false
    this.reasoningEffort = MapUtil.toString("reasoning-effort", config)
    this.client = createClient(apiKey, model, config)

    LOGGER.info("LLM: " + config["type"])
    LOGGER.info(" model: $model")
    LOGGER.info(" streaming: ${this.streamingEnabled}")
    LOGGER.info(" thinking: ${this.thinking}")
    if (this.reasoningEffort != null) {
        LOGGER.info(" reasoning_effort: ${this.reasoningEffort}")
    }
}
```

- [ ] **Step 4: Run tests — expect green**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=DeepseekTest
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/llm/deepseek/Deepseek.kt \
        src/test/kotlin/com/wutsi/kokibot/llm/deepseek/DeepseekTest.kt
git commit -m "feat: Deepseek/Gemini/Kimi read api-key from CredentialService"
```

---

## Task 4: Update TelegramChannel

**Files:**

- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannel.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannelTest.kt`

**Interfaces:**

- Consumes: `Context.credentialService: CredentialService` from Task 2

---

- [ ] **Step 1: Update `TelegramChannelTest` to inject mock `CredentialService`**

In `src/test/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannelTest.kt`, update the setup:

```kotlin
import com.wutsi.kokibot.service.credential.CredentialService
// ... existing imports ...

class TelegramChannelTest {
    private val app = mock<TelegramBotsLongPollingApplication>()
    private val client = mock<TelegramClient>()
    private val factory = mock<TelegramFactory>()
    private val rest = mock<RestTemplate>()
    private val restBuilder = mock<RestBuilder>()
    private val botToken = "13200493:AAH-abc123def456ghi789jkl012mno345pqr"
    private val config = mapOf(
        // token removed
        "bot-name" to "test-bot",
    )
    private val credentialService = mock<CredentialService>()
    private val inbox = mock<Inbox>()
    private val context = Context(
        home = File("target/test-data/telegram"),
        llm = mock(),
        fileService = mock(),
        assistant = mock(),
        inbox = inbox,
        credentialService = credentialService,
    )
    // ... rest unchanged ...

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("channel.telegram")).doReturn(botToken)
        // ... existing setUp calls ...
    }
```

- [ ] **Step 2: Run tests — expect failure**

```bash
mvn test -Dtest=TelegramChannelTest
```

Expected: `init` test fails because `TelegramChannel.init()` still reads `config["token"]`.

- [ ] **Step 3: Update `TelegramChannel.init()` to use `credentialService`**

In `src/main/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannel.kt`, change the `init()` method. Replace:

```kotlin
val token = config["token"]?.toString() ?: throw ConfigurationException("token is required")
```

With:

```kotlin
val token = context.credentialService.get("channel.telegram")
```

- [ ] **Step 4: Run tests — expect green**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=TelegramChannelTest
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannel.kt \
        src/test/kotlin/com/wutsi/kokibot/channel/telegram/TelegramChannelTest.kt
git commit -m "feat: TelegramChannel reads token from CredentialService"
```

---

## Task 5: Update EmailChannel

**Files:**

- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/email/EmailChannel.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/channel/email/EmailChannelTest.kt`

**Interfaces:**

- Consumes: `Context.credentialService: CredentialService` from Task 2

**Note:** Only `password` moves to `CredentialService`. `username` stays in `config`.

---

- [ ] **Step 1: Update `EmailChannelTest` to inject mock `CredentialService`**

In `src/test/kotlin/com/wutsi/kokibot/channel/email/EmailChannelTest.kt`, update the setup:

```kotlin
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.service.credential.CredentialService
// ... existing imports ...

class EmailChannelTest {
    private val channel = EmailChannel()
    private lateinit var greenMail: GreenMail
    private lateinit var guser: GreenMailUser

    private val email = "test@example.com"
    private val username = "user"
    private val password = "password"
    private val credentialService = mock<CredentialService>()
    val config = mapOf(
        // password removed
        "email" to email,
        "username" to username,

        "imap-protocol" to "imap",
        "imap-host" to "localhost",
        "imap-port" to ServerSetupTest.IMAP.port,
        "imap-ssl" to false,

        "smtp-host" to "localhost",
        "smtp-port" to ServerSetupTest.SMTP.port,
        "smtp-ssl" to false,
    )
    private val inbox = mock(Inbox::class.java)
    private val context = Context(
        home = File("target/test-data/email-channel"),
        llm = mock(),
        assistant = mock(),
        inbox = inbox,
        credentialService = credentialService,
    )

    @BeforeEach
    fun setup() {
        whenever(credentialService.get("channel.email.password")).doReturn(password)
        greenMail = GreenMail(ServerSetupTest.ALL)
        guser = greenMail.setUser(email, username, password)
        greenMail.start()

        context.fileService.init(config, context)
        channel.init(config, context)
    }
    // ... rest unchanged ...
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
mvn test -Dtest=EmailChannelTest
```

Expected: tests fail because `EmailChannel.init()` still reads `config["password"]`.

- [ ] **Step 3: Update `EmailChannel.init()` to use `credentialService`**

In `src/main/kotlin/com/wutsi/kokibot/channel/email/EmailChannel.kt`, find where `password` is read from config:

```kotlin
password = config["password"] as? String
    ?: throw ConfigurationException("password is required")
```

Replace with:

```kotlin
password = context.credentialService.get("channel.email.password")
```

- [ ] **Step 4: Run tests — expect green**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=EmailChannelTest
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/channel/email/EmailChannel.kt \
        src/test/kotlin/com/wutsi/kokibot/channel/email/EmailChannelTest.kt
git commit -m "feat: EmailChannel reads password from CredentialService"
```

---

## Task 6: Update McpServer

**Files:**

- Modify: `src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt`

**Interfaces:**

- Consumes: `Context.credentialService: CredentialService` from Task 2

---

- [ ] **Step 1: Update `McpServerTest` to use a real `Context` with mock `CredentialService`**

In `src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt`, replace the anonymous `mock()` context with a real
`Context`:

```kotlin
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.service.credential.CredentialService
import java.io.File
// ... existing imports ...

class McpServerTest {
    private val transport = mock<McpHttpTransport>()
    private val credentialService = mock<CredentialService>()
    private val context = Context(
        home = File("target/test-data/mcp"),
        llm = mock(),
        credentialService = credentialService,
    )

    private val configMap = mapOf(
        // token removed
        "name" to "weather-mcp",
        "description" to "Weather data",
        "url" to "https://weather.example.com/mcp",
    )
    // ... rest unchanged ...

    @BeforeEach
    fun setUp() {
        whenever(credentialService.getOrNull("mcp.weather-mcp")).doReturn("tok-123")
        server.init(configMap, context)
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
    }
```

- [ ] **Step 2: Run tests — expect failure**

```bash
mvn test -Dtest=McpServerTest
```

Expected: fails because `McpServer.init()` still reads `config["token"]`.

- [ ] **Step 3: Update `McpServer.init()` to use `credentialService`**

In `src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt`, update `init()`:

```kotlin
override fun init(config: Map<*, *>, context: Context) {
    val name = config["name"]?.toString()
        ?: throw IllegalArgumentException("'name' is required")
    val url = config["url"]?.toString()
        ?: throw IllegalArgumentException("'url' is required")
    this.config = McpServerConfig(
        name = name,
        description = config["description"]?.toString() ?: "",
        url = url,
        token = context.credentialService.getOrNull("mcp.$name"),
        icon = config["icon"]?.toString() ?: "",
    )
}
```

- [ ] **Step 4: Run tests — expect green**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=McpServerTest
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Run the full test suite**

```bash
mvn clean install
```

Expected: `BUILD SUCCESS`, all tests pass, coverage ≥ 90%.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt \
        src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt
git commit -m "feat: McpServer reads token from CredentialService"
```
