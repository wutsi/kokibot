# MCP CallProxy Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-tool `McpTool` registration in `ToolRegistry` with a single generic `McpCallTool` proxy, injecting tool descriptions as system-prompt text instead of LLM tool schemas, to reduce token usage with large MCP servers.

**Architecture:** `McpServer.activate(toolRegistry)` is replaced by `McpServer.initialize()`, which stores `toolDefinitions` on the server object. `Context` gains an `activatedMcps: MutableList<McpServer>` field. `McpActivationTool` calls `initialize()` and appends the server to `activatedMcps`. A new `McpCallTool` reads `activatedMcps` at call time to route `server`/`tool`/`arguments` to the correct `McpClient`. `PromptBuilder.mcpInstructions()` renders available (not yet activated) servers and activated servers with their full tool descriptions as Markdown text.

**Tech Stack:** Kotlin, JUnit 5, Mockito-Kotlin (`com.nhaarman.mockitokotlin2`), Maven

## Global Constraints

- Package prefix: `com.wutsi.kokibot.mcp`
- Test library: `com.nhaarman.mockitokotlin2` (never `org.mockito.kotlin`)
- TDD: write failing test first, verify it fails, then implement
- All 707+ tests must pass: `cd /Users/htchepannou/Perso/kokibot && mvn test -pl . 2>&1 | tail -10`
- ktlint must pass (no style violations)
- Final commit message: `"refactor(mcp): replace McpTool with McpCallTool proxy to reduce token usage"`
- Base directory for all source files: `/Users/htchepannou/Perso/kokibot`

---

### Task 1: Refactor `McpServer` — replace `activate()` with `initialize()` + `toolDefinitions`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt`

**Interfaces:**
- Produces: `McpServer.initialize()` — no-op if already called (idempotent via `::client.isInitialized` guard)
- Produces: `McpServer.toolDefinitions: List<McpToolDefinition>` — populated after `initialize()`
- Removes: `McpServer.activate(toolRegistry: ToolRegistry)` and `McpServer.activated: Boolean`

- [ ] **Step 1: Write the failing tests**

Replace `src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt` with:

```kotlin
package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpServerTest {
    private val transport = mock<McpHttpTransport>()

    private val config = McpServerConfig(
        name = "weather-mcp",
        description = "Weather data",
        url = "https://weather.example.com/mcp",
        token = "tok-123",
    )

    private val initResponse = McpHttpResponse(
        statusCode = 200,
        headers = mapOf("Mcp-Session-Id" to "sess-1"),
        body = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"weather","version":"1.0"}}}""",
    )

    private val listToolsResponse = McpHttpResponse(
        statusCode = 200,
        headers = emptyMap(),
        body = """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_weather","description":"Get weather"},{"name":"get_forecast","description":"Get forecast"}]}}""",
    )

    private val server = McpServer(config, transport)

    @BeforeEach
    fun setUp() {
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
    }

    @Test
    fun `id returns mcp-prefixed name`() {
        assertEquals("mcp:weather-mcp", server.id())
    }

    @Test
    fun `health returns up`() {
        assertTrue(server.health().up)
    }

    @Test
    fun `initialize sets toolDefinitions`() {
        server.initialize()

        assertEquals(2, server.toolDefinitions.size)
        assertEquals("get_weather", server.toolDefinitions[0].name)
        assertEquals("get_forecast", server.toolDefinitions[1].name)
    }

    @Test
    fun `initialize is no-op when called twice`() {
        doReturn(initResponse).doReturn(listToolsResponse).whenever(transport).post(any(), any(), any())
        server.initialize()

        // Second call — transport must NOT be called again
        server.initialize()

        // Only 2 transport calls total (init + listTools), not 4
        verify(transport, times(2)).post(any(), any(), any())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpServerTest 2>&1 | tail -20
```

Expected: FAIL — `initialize` method not found, `toolDefinitions` property not found.

- [ ] **Step 3: Implement `McpServer.initialize()`**

Replace `src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt` with:

```kotlin
package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import org.slf4j.LoggerFactory

class McpServer(
    val config: McpServerConfig,
    private val transport: McpHttpTransport = McpOkHttpTransport(),
) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpServer::class.java)
    }

    var toolDefinitions: List<McpToolDefinition> = emptyList()
        private set

    lateinit var client: McpClient
        private set

    override fun id(): String = "mcp:${config.name}"

    override fun init(config: Map<*, *>, context: Context) {}

    override fun health(): Health = Health(id = id(), up = true)

    @Synchronized
    fun initialize() {
        if (::client.isInitialized) return
        LOGGER.info("Initializing MCP server: ${config.name}")
        client = McpClient(config.url, config.token, transport)
        client.initialize()
        toolDefinitions = client.listTools()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpServerTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/mcp/McpServer.kt src/test/kotlin/com/wutsi/kokibot/mcp/McpServerTest.kt && git commit -m "refactor(mcp): replace McpServer.activate() with initialize() + toolDefinitions"
```

---

### Task 2: Add `activatedMcps` to `Context`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Context.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/ContextTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 (independent change)
- Produces: `Context.activatedMcps: MutableList<McpServer>` — a `CopyOnWriteArrayList` default

- [ ] **Step 1: Write the failing test**

Open `src/test/kotlin/com/wutsi/kokibot/ContextTest.kt`.

Add this import at the top of the file alongside existing imports:
```kotlin
import com.wutsi.kokibot.mcp.McpServer
import java.util.concurrent.CopyOnWriteArrayList
```

Add this test method inside `class ContextTest`:

```kotlin
@Test
fun `activatedMcps is empty by default`() {
    assertTrue(context.activatedMcps.isEmpty())
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=ContextTest#activatedMcps_is_empty_by_default 2>&1 | tail -20
```

Expected: FAIL — `activatedMcps` property not found.

- [ ] **Step 3: Add `activatedMcps` to `Context`**

In `src/main/kotlin/com/wutsi/kokibot/Context.kt`, add the import:

```kotlin
import java.util.concurrent.CopyOnWriteArrayList
```

Then add the `activatedMcps` parameter to the primary constructor, after the `assistantRegistry` parameter:

```kotlin
    val activatedMcps: MutableList<McpServer> = CopyOnWriteArrayList(),
```

The full constructor signature after the change should have `activatedMcps` as the last parameter.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=ContextTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all ContextTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/Context.kt src/test/kotlin/com/wutsi/kokibot/ContextTest.kt && git commit -m "feat(context): add activatedMcps list for per-request MCP tracking"
```

---

### Task 3: Clear `activatedMcps` at the start of each `ReActReasoningLoop.execute()`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt`

**Interfaces:**
- Consumes: `Context.activatedMcps` from Task 2
- Produces: `activatedMcps` is cleared before iteration begins

- [ ] **Step 1: Read the existing test to understand structure**

```bash
head -60 /Users/htchepannou/Perso/kokibot/src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt
```

- [ ] **Step 2: Write the failing test**

Open `src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt`.

Locate the `setUp()` method. Add a mock `activatedMcps` stub. Then add this test:

```kotlin
@Test
fun `execute clears activatedMcps at start`() {
    val mcpList = mutableListOf<com.wutsi.kokibot.mcp.McpServer>(mock())
    doReturn(mcpList).whenever(context).activatedMcps

    // stub LLM to return STOP immediately
    val response = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = "done",
                finishReason = LLMFinishReason.STOP,
                toolCalls = emptyList()
            )
        )
    )
    doReturn(response).whenever(context.llm).completion(any(), any())

    loop.execute(
        query = Message(text = "hello"),
        streamCallback = null,
        startIteration = 0,
        memory = mutableListOf(),
        context = context
    )

    assertTrue(mcpList.isEmpty())
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=ReActReasoningLoopTest#execute_clears_activatedMcps_at_start 2>&1 | tail -20
```

Expected: FAIL — `activatedMcps` not cleared.

- [ ] **Step 4: Add `context.activatedMcps.clear()` to `execute()`**

In `src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt`, add the clear call at the top of the `execute()` method body, before the `var iteration = startIteration` line:

```kotlin
override fun execute(
    query: Message,
    streamCallback: ((LLMStreamData) -> Unit)?,
    startIteration: Int,
    memory: MutableList<String>,
    context: Context
): Message {
    context.activatedMcps.clear()
    var iteration = startIteration
    // ... rest of existing code unchanged
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=ReActReasoningLoopTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all ReActReasoningLoopTest tests pass.

- [ ] **Step 6: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt && git commit -m "feat(mcp): clear activatedMcps at start of each reasoning loop execution"
```

---

### Task 4: Refactor `McpActivationTool` to use `initialize()` + `activatedMcps`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/mcp/McpActivationTool.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/mcp/McpActivationToolTest.kt`

**Interfaces:**
- Consumes: `McpServer.initialize()` from Task 1, `Context.activatedMcps` from Task 2
- Produces: `McpActivationTool.exec()` calls `server.initialize()`, appends server to `context.activatedMcps`, returns tool name list using `server.toolDefinitions`

- [ ] **Step 1: Write the failing tests**

Replace `src/test/kotlin/com/wutsi/kokibot/mcp/McpActivationToolTest.kt` with:

```kotlin
package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpActivationToolTest {
    private val mcpRegistry = mock<McpRegistry>()
    private val server = mock<McpServer>()
    private val context = mock<Context>()
    private val activatedMcps = mutableListOf<McpServer>()

    private val tool = McpActivationTool()

    @BeforeEach
    fun setUp() {
        doReturn(mcpRegistry).whenever(context).mcpRegistry
        doReturn(activatedMcps).whenever(context).activatedMcps
        doReturn(server).whenever(mcpRegistry).get("weather-mcp")
        doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server).config
        doReturn(
            listOf(
                McpToolDefinition(name = "get_weather", description = "Get weather"),
                McpToolDefinition(name = "get_forecast", description = "Get forecast"),
            )
        ).whenever(server).toolDefinitions
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(McpActivationTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)
        assertEquals("server", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `exec initializes server and adds to activatedMcps`() {
        val result = tool.exec(mapOf("server" to "weather-mcp"))

        verify(server).initialize()
        assertTrue(activatedMcps.contains(server))
        assertTrue(result.contains("weather-mcp"))
        assertTrue(result.contains("Activated"))
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("get_forecast"))
        assertTrue(result.contains("mcp_call"))
    }

    @Test
    fun `exec does not add duplicate server to activatedMcps`() {
        activatedMcps.add(server) // already activated

        tool.exec(mapOf("server" to "weather-mcp"))

        assertEquals(1, activatedMcps.size)
    }

    @Test
    fun `exec returns no tools message when toolDefinitions is empty`() {
        doReturn(emptyList<McpToolDefinition>()).whenever(server).toolDefinitions

        val result = tool.exec(mapOf("server" to "weather-mcp"))

        assertTrue(result.contains("No tools available"))
    }

    @Test
    fun `exec returns error string when server not found`() {
        doThrow(McpNotFoundException("MCP server not found: unknown")).whenever(mcpRegistry).get("unknown")

        val result = tool.exec(mapOf("server" to "unknown"))

        assertTrue(result.contains("Unable to activate"))
        assertTrue(result.contains("unknown"))
    }

    @Test
    fun `exec returns error string when initialization fails`() {
        doThrow(RuntimeException("Connection refused")).whenever(server).initialize()

        val result = tool.exec(mapOf("server" to "weather-mcp"))

        assertTrue(result.contains("Unable to activate"))
        assertTrue(result.contains("Connection refused"))
    }

    @Test
    fun `exec throws when server parameter is missing`() {
        try {
            tool.exec(emptyMap<String, Any>())
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `exec throws when server parameter is empty`() {
        try {
            tool.exec(mapOf("server" to ""))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `statusText returns activation message`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = McpActivationTool.NAME, arguments = mapOf("server" to "weather-mcp"))
        )
        val result = tool.statusText(toolCalls)
        assertTrue(result.contains("weather-mcp"))
    }

    @Test
    fun `activate returns true`() {
        assertTrue(tool.activate())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpActivationToolTest 2>&1 | tail -20
```

Expected: FAIL — `activatedMcps` not found, `initialize()` not called.

- [ ] **Step 3: Implement refactored `McpActivationTool`**

Replace `src/main/kotlin/com/wutsi/kokibot/mcp/McpActivationTool.kt` with:

```kotlin
package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class McpActivationTool : Tool {
    companion object {
        const val NAME = "mcp_activate"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Activates an MCP server and makes its tools available. Call this before using any tools from an MCP server.",
        parameters = listOf(
            ToolParameter(
                name = "server",
                description = "Name of the MCP server to activate",
                type = ToolParameterType.STRING,
                required = true,
            ),
        ),
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val name = toolCalls.firstOrNull()?.arguments?.get("server")?.toString() ?: "MCP server"
        return "Activating MCP server: $name"
    }

    override fun exec(arguments: Map<*, *>): String {
        val name = arguments["server"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: server")
        return try {
            val server = context.mcpRegistry.get(name)
            server.initialize()
            if (!context.activatedMcps.contains(server)) context.activatedMcps.add(server)
            val tools = server.toolDefinitions.map { it.name }
            "Activated MCP server `$name`. ${
                if (tools.isEmpty()) {
                    "No tools available."
                } else {
                    "Tools: ${tools.joinToString(", ")}. Use mcp_call to invoke them."
                }
            }"
        } catch (ex: Exception) {
            "Unable to activate MCP server `$name`. Error: ${ex.message}"
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpActivationToolTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all McpActivationToolTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/mcp/McpActivationTool.kt src/test/kotlin/com/wutsi/kokibot/mcp/McpActivationToolTest.kt && git commit -m "refactor(mcp): McpActivationTool uses initialize() + activatedMcps"
```

---

### Task 5: Create `McpCallTool` + `McpCallToolTest`

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/mcp/McpCallTool.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/mcp/McpCallToolTest.kt`

**Interfaces:**
- Consumes: `Context.activatedMcps` from Task 2, `McpServer.client.callTool(name, args)` from Task 1
- Produces: `McpCallTool` — a `Tool` with `NAME = "mcp_call"`, `activate()` returns true iff `activatedMcps` is non-empty

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/wutsi/kokibot/mcp/McpCallToolTest.kt`:

```kotlin
package com.wutsi.kokibot.mcp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpCallToolTest {
    private val mcpRegistry = mock<McpRegistry>()
    private val mcpClient = mock<McpClient>()
    private val server = mock<McpServer>()
    private val context = mock<Context>()
    private val activatedMcps = mutableListOf<McpServer>()

    private val tool = McpCallTool()

    @BeforeEach
    fun setUp() {
        doReturn(mcpRegistry).whenever(context).mcpRegistry
        doReturn(activatedMcps).whenever(context).activatedMcps
        doReturn(server).whenever(mcpRegistry).get("weather-mcp")
        doReturn(mcpClient).whenever(server).client
        doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server).config
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun `metadata returns correct name and parameters`() {
        val meta = tool.metadata()
        assertEquals(McpCallTool.NAME, meta.name)
        assertEquals("mcp_call", meta.name)
        assertEquals(3, meta.parameters.size)

        val serverParam = meta.parameters.first { it.name == "server" }
        assertEquals(ToolParameterType.STRING, serverParam.type)
        assertTrue(serverParam.required)

        val toolParam = meta.parameters.first { it.name == "tool" }
        assertEquals(ToolParameterType.STRING, toolParam.type)
        assertTrue(toolParam.required)

        val argsParam = meta.parameters.first { it.name == "arguments" }
        assertEquals(ToolParameterType.OBJECT, argsParam.type)
        assertFalse(argsParam.required)
    }

    @Test
    fun `activate returns false when no activated MCPs`() {
        assertFalse(tool.activate())
    }

    @Test
    fun `activate returns true when MCPs activated`() {
        activatedMcps.add(server)
        assertTrue(tool.activate())
    }

    @Test
    fun `exec calls callTool on the correct server`() {
        activatedMcps.add(server)
        doReturn("Sunny, 72F").whenever(mcpClient).callTool(any(), any())

        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
                "arguments" to mapOf("city" to "Seattle"),
            )
        )

        assertEquals("Sunny, 72F", result)
        verify(mcpClient).callTool(eq("get_weather"), eq(mapOf("city" to "Seattle")))
    }

    @Test
    fun `exec returns error when server not activated`() {
        // activatedMcps is empty — server is registered but not activated
        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("not activated"))
        assertTrue(result.contains("weather-mcp"))
        assertTrue(result.contains("mcp_activate"))
    }

    @Test
    fun `exec returns error when server not found`() {
        activatedMcps.add(server)
        doThrow(McpNotFoundException("MCP server not found: unknown")).whenever(mcpRegistry).get("unknown")

        val result = tool.exec(
            mapOf(
                "server" to "unknown",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("Error calling tool"))
        assertTrue(result.contains("unknown"))
    }

    @Test
    fun `exec returns error when callTool throws`() {
        activatedMcps.add(server)
        doThrow(RuntimeException("connection refused")).whenever(mcpClient).callTool(any(), any())

        val result = tool.exec(
            mapOf(
                "server" to "weather-mcp",
                "tool" to "get_weather",
            )
        )

        assertTrue(result.contains("Error calling tool"))
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("connection refused"))
    }

    @Test
    fun `exec throws when server argument missing`() {
        try {
            tool.exec(mapOf("tool" to "get_weather"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `exec throws when tool argument missing`() {
        try {
            tool.exec(mapOf("server" to "weather-mcp"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `statusText returns descriptive string`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "1",
                name = McpCallTool.NAME,
                arguments = mapOf("server" to "weather-mcp", "tool" to "get_weather")
            )
        )
        val result = tool.statusText(toolCalls)
        assertTrue(result.contains("get_weather"))
        assertTrue(result.contains("weather-mcp"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpCallToolTest 2>&1 | tail -20
```

Expected: FAIL — `McpCallTool` class not found.

- [ ] **Step 3: Create `McpCallTool`**

Create `src/main/kotlin/com/wutsi/kokibot/mcp/McpCallTool.kt`:

```kotlin
package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class McpCallTool : Tool {
    companion object {
        const val NAME = "mcp_call"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata() = ToolMetadata(
        name = NAME,
        description = "Call a tool on an activated MCP server.",
        parameters = listOf(
            ToolParameter("server", "Name of the activated MCP server", ToolParameterType.STRING, required = true),
            ToolParameter("tool", "Name of the tool to call on the MCP server", ToolParameterType.STRING, required = true),
            ToolParameter("arguments", "Arguments to pass to the tool", ToolParameterType.OBJECT, required = false),
        ),
    )

    override fun activate(): Boolean = context.activatedMcps.isNotEmpty()

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val server = toolCalls.firstOrNull()?.arguments?.get("server") ?: "?"
        val tool = toolCalls.firstOrNull()?.arguments?.get("tool") ?: "?"
        return "Calling MCP tool $tool on $server"
    }

    override fun exec(arguments: Map<*, *>): String {
        val serverName = arguments["server"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: server")
        val toolName = arguments["tool"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: tool")
        val toolArgs = arguments["arguments"] as? Map<*, *> ?: emptyMap<String, Any>()

        return try {
            val server = context.mcpRegistry.get(serverName)
            if (!context.activatedMcps.contains(server)) {
                return "MCP server `$serverName` is not activated. Call mcp_activate first."
            }
            server.client.callTool(toolName, toolArgs)
        } catch (ex: Exception) {
            "Error calling tool `$toolName` on `$serverName`: ${ex.message}"
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=McpCallToolTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all McpCallToolTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/mcp/McpCallTool.kt src/test/kotlin/com/wutsi/kokibot/mcp/McpCallToolTest.kt && git commit -m "feat(mcp): add McpCallTool proxy for routing mcp_call to activated servers"
```

---

### Task 6: Refactor `PromptBuilder.mcpInstructions()` with available/activated split

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt`

**Interfaces:**
- Consumes: `Context.activatedMcps` from Task 2, `McpServer.toolDefinitions` from Task 1
- Produces: updated `mcpInstructions()` rendering available servers (not in `activatedMcps`) and activated servers (in `activatedMcps`) with full tool descriptions

- [ ] **Step 1: Write the failing tests**

Open `src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt`.

In `setUp()`, add a stub for `context.activatedMcps`:

```kotlin
doReturn(mutableListOf<McpServer>()).whenever(context).activatedMcps
```

Replace the two existing MCP test methods (`should include available MCP servers in system instructions` and `should omit MCP section when no servers configured`) with these five tests:

```kotlin
@Test
fun `should include available MCP servers when none activated`() {
    val localMcpRegistry = mock<McpRegistry>()
    val server1 = mock<McpServer>()
    val server2 = mock<McpServer>()
    doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data and forecasts", url = "https://w.example.com")).whenever(server1).config
    doReturn(McpServerConfig(name = "news-mcp", description = "Latest news", url = "https://n.example.com")).whenever(server2).config
    doReturn(listOf(server1, server2)).whenever(localMcpRegistry).all()
    doReturn(localMcpRegistry).whenever(context).mcpRegistry
    doReturn(mutableListOf<McpServer>()).whenever(context).activatedMcps

    val query = Message(userId = "user1", channelId = "channel1")
    val instructions = builder.buildSystemInstructions(query = query, coordinator = false, context = context)

    assertTrue(instructions.contains("# Available MCP Servers"))
    assertTrue(instructions.contains("weather-mcp"))
    assertTrue(instructions.contains("Weather data and forecasts"))
    assertTrue(instructions.contains("news-mcp"))
    assertTrue(instructions.contains("Latest news"))
    assertTrue(instructions.contains("mcp_activate"))
    assertFalse(instructions.contains("# Activated MCP Servers"))
}

@Test
fun `should include activated MCP servers with tool descriptions`() {
    val localMcpRegistry = mock<McpRegistry>()
    val server = mock<McpServer>()
    doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(server).config
    doReturn(
        listOf(
            McpToolDefinition(
                name = "get_weather",
                description = "Get current weather",
                inputSchema = mapOf(
                    "properties" to mapOf(
                        "city" to mapOf("type" to "string", "description" to "City name")
                    )
                )
            )
        )
    ).whenever(server).toolDefinitions
    doReturn(listOf(server)).whenever(localMcpRegistry).all()
    doReturn(localMcpRegistry).whenever(context).mcpRegistry
    doReturn(mutableListOf(server)).whenever(context).activatedMcps

    val query = Message(userId = "user1", channelId = "channel1")
    val instructions = builder.buildSystemInstructions(query = query, coordinator = false, context = context)

    assertTrue(instructions.contains("# Activated MCP Servers"))
    assertTrue(instructions.contains("weather-mcp"))
    assertTrue(instructions.contains("get_weather"))
    assertTrue(instructions.contains("Get current weather"))
    assertTrue(instructions.contains("city"))
    assertTrue(instructions.contains("mcp_call"))
    assertFalse(instructions.contains("# Available MCP Servers"))
}

@Test
fun `should split servers into available and activated sections`() {
    val localMcpRegistry = mock<McpRegistry>()
    val available = mock<McpServer>()
    val activated = mock<McpServer>()
    doReturn(McpServerConfig(name = "news-mcp", description = "Latest news", url = "https://n.example.com")).whenever(available).config
    doReturn(McpServerConfig(name = "weather-mcp", description = "Weather data", url = "https://w.example.com")).whenever(activated).config
    doReturn(emptyList<McpToolDefinition>()).whenever(activated).toolDefinitions
    doReturn(listOf(available, activated)).whenever(localMcpRegistry).all()
    doReturn(localMcpRegistry).whenever(context).mcpRegistry
    doReturn(mutableListOf(activated)).whenever(context).activatedMcps

    val query = Message(userId = "user1", channelId = "channel1")
    val instructions = builder.buildSystemInstructions(query = query, coordinator = false, context = context)

    assertTrue(instructions.contains("# Available MCP Servers"))
    assertTrue(instructions.contains("news-mcp"))
    assertTrue(instructions.contains("# Activated MCP Servers"))
    assertTrue(instructions.contains("weather-mcp"))
}

@Test
fun `should omit MCP section when no servers configured`() {
    val localMcpRegistry = mock<McpRegistry>()
    doReturn(emptyList<McpServer>()).whenever(localMcpRegistry).all()
    doReturn(localMcpRegistry).whenever(context).mcpRegistry
    doReturn(mutableListOf<McpServer>()).whenever(context).activatedMcps

    val query = Message(userId = "user1", channelId = "channel1")
    val instructions = builder.buildSystemInstructions(query = query, coordinator = false, context = context)

    assertFalse(instructions.contains("# Available MCP Servers"))
    assertFalse(instructions.contains("# Activated MCP Servers"))
}

@Test
fun `should include MCP tool inputSchema parameters`() {
    val localMcpRegistry = mock<McpRegistry>()
    val server = mock<McpServer>()
    doReturn(McpServerConfig(name = "weather-mcp", description = "Weather", url = "https://w.example.com")).whenever(server).config
    doReturn(
        listOf(
            McpToolDefinition(
                name = "get_weather",
                description = "Get weather info",
                inputSchema = mapOf(
                    "properties" to mapOf(
                        "city" to mapOf("type" to "string", "description" to "Name of the city"),
                        "units" to mapOf("type" to "string", "description" to "Temperature units"),
                    )
                )
            )
        )
    ).whenever(server).toolDefinitions
    doReturn(listOf(server)).whenever(localMcpRegistry).all()
    doReturn(localMcpRegistry).whenever(context).mcpRegistry
    doReturn(mutableListOf(server)).whenever(context).activatedMcps

    val query = Message(userId = "user1", channelId = "channel1")
    val instructions = builder.buildSystemInstructions(query = query, coordinator = false, context = context)

    assertTrue(instructions.contains("city"))
    assertTrue(instructions.contains("Name of the city"))
    assertTrue(instructions.contains("units"))
    assertTrue(instructions.contains("Temperature units"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=PromptBuilderTest 2>&1 | tail -20
```

Expected: several new tests FAIL.

- [ ] **Step 3: Implement updated `mcpInstructions()` in `PromptBuilder`**

In `src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt`, replace the existing `mcpInstructions()` private method with:

```kotlin
private fun mcpInstructions(context: Context): String? {
    val allServers = context.mcpRegistry.all()
    if (allServers.isEmpty()) return null

    val sb = StringBuilder()

    val available = allServers.filter { !context.activatedMcps.contains(it) }
    if (available.isNotEmpty()) {
        sb.append("# Available MCP Servers\n\n")
        sb.append("Activate with `mcp_activate`:\n\n")
        available.forEach { server ->
            sb.append("## ${server.config.name}\n\n**Description:** ${server.config.description}\n\n")
        }
    }

    if (context.activatedMcps.isNotEmpty()) {
        sb.append("# Activated MCP Servers\n\n")
        sb.append("Use `mcp_call` to invoke tools:\n\n")
        context.activatedMcps.forEach { server ->
            sb.append("## ${server.config.name}\n\n")
            server.toolDefinitions.forEach { tool ->
                sb.append("### ${tool.name}\n")
                tool.description?.let { sb.append("$it\n\n") }
                val props = tool.inputSchema["properties"] as? Map<*, *>
                props?.forEach { (k, v) ->
                    val def = v as? Map<*, *>
                    sb.append("- `$k` (${def?.get("type") ?: "string"}): ${def?.get("description") ?: ""}\n")
                }
                sb.append("\n")
            }
        }
    }

    return sb.toString().ifEmpty { null }
}
```

Also add `McpToolDefinition` to the import block if not already present — no new import needed since `McpServer` is already imported.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=PromptBuilderTest 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all PromptBuilderTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt && git commit -m "feat(mcp): inject activated MCP tool descriptions as system prompt text"
```

---

### Task 7: Register `McpCallTool` in `ContextFactory` and delete `McpTool`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt`
- Delete: `src/main/kotlin/com/wutsi/kokibot/mcp/McpTool.kt`
- Delete: `src/test/kotlin/com/wutsi/kokibot/mcp/McpToolTest.kt`

**Interfaces:**
- Consumes: `McpCallTool` from Task 5
- Produces: `discoverTools()` returns 13 tools (was 12); `McpTool` and `McpToolTest` deleted

- [ ] **Step 1: Update `ContextFactoryTest` to expect 13 tools**

In `src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt`, change line:

```kotlin
verify(toolRegistry, times(12)).register(any())
```

to:

```kotlin
verify(toolRegistry, times(13)).register(any())
```

- [ ] **Step 2: Run tests to verify it fails**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . -Dtest=ContextFactoryTest 2>&1 | tail -20
```

Expected: FAIL — `Wanted 13 times but was 12`.

- [ ] **Step 3: Add `McpCallTool` to `discoverTools()` and update imports**

In `src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt`, add the import:

```kotlin
import com.wutsi.kokibot.mcp.McpCallTool
```

In `discoverTools()`, add `McpCallTool()` after `McpActivationTool()`:

```kotlin
McpActivationTool(),
McpCallTool(),
SkillActivationTool(),
```

- [ ] **Step 4: Delete `McpTool.kt` and `McpToolTest.kt`**

```bash
rm /Users/htchepannou/Perso/kokibot/src/main/kotlin/com/wutsi/kokibot/mcp/McpTool.kt
rm /Users/htchepannou/Perso/kokibot/src/test/kotlin/com/wutsi/kokibot/mcp/McpToolTest.kt
```

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . 2>&1 | tail -10
```

Expected: BUILD SUCCESS. No remaining references to `McpTool` (previously only used in `McpActivationTool.exec()`, which has already been replaced in Task 4).

- [ ] **Step 6: Commit**

```bash
cd /Users/htchepannou/Perso/kokibot && git add src/main/kotlin/com/wutsi/kokibot/ContextFactory.kt src/test/kotlin/com/wutsi/kokibot/ContextFactoryTest.kt && git rm src/main/kotlin/com/wutsi/kokibot/mcp/McpTool.kt src/test/kotlin/com/wutsi/kokibot/mcp/McpToolTest.kt && git commit -m "refactor(mcp): replace McpTool with McpCallTool proxy to reduce token usage"
```

---

### Task 8: Final verification — full suite + ktlint

**Files:** None new

- [ ] **Step 1: Run full test suite**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn test -pl . 2>&1 | tail -10
```

Expected: BUILD SUCCESS. Test count ≥ 707 (net: +9 new McpCallToolTest + new McpServerTest/McpActivationToolTest tests - removed McpToolTest tests).

- [ ] **Step 2: Check for any stale references to `McpTool` or `activated` field**

```bash
grep -r "McpTool\b\|\.activated\b\|toolRegistry\)" /Users/htchepannou/Perso/kokibot/src --include="*.kt" | grep -v "McpCallTool\|McpActivationTool\|toolRegistry\b"
```

Expected: no output (all references cleaned up).

- [ ] **Step 3: Run ktlint**

```bash
cd /Users/htchepannou/Perso/kokibot && mvn antrun:run@ktlint-check -pl . 2>&1 | tail -20
```

If ktlint reports violations, fix them (typically trailing spaces or import ordering), then re-run.

- [ ] **Step 4: Tag the work**

No tag needed — the series of commits from Tasks 1–7 is the complete delivery.
