# Parallel Tool Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the assistant to execute independent tool calls in parallel instead of sequentially, reducing overall response time for multi-tool requests.

**Architecture:** Introduce a thread pool executor in the Assistant to handle tool execution concurrently. The assistant will analyze tool calls from LLM responses, identify independent calls (no data dependencies), and submit them to the thread pool. Results are collected and added to iteration memory only after all parallel calls complete. Configuration will support thread pool sizing and can be disabled for debugging.

**Tech Stack:** Kotlin 2.3.21, Java 17 Executors framework, JUnit 5 + Mockito for testing

---

## Current Flow Analysis

**Sequential execution (current state):**
1. LLM returns response with N tool calls
2. `decide()` method identifies tool calls
3. `exec(choice)` iterates through calls sequentially
4. Each `exec(call)` blocks until tool completes
5. Memory updated after each tool
6. Loop back to LLM with accumulated results

**Problem:** If LLM returns 3 independent tool calls (e.g., weather for 3 cities), they execute in 3 sequential time units instead of 1 parallel unit.

**Solution:** Execute independent tool calls concurrently using a thread pool.

---

## Task 1: Add Thread Pool Configuration

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt:39-54`
- Modify: `src/main/resources/application.properties` (if exists, otherwise document in CLAUDE.md)
- Test: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

- [ ] **Step 1: Add thread pool fields to Assistant class**

In `Assistant.kt`, add these fields after line 34:

```kotlin
private var threadPoolSize: Int = 4 // Default: 4 threads
private lateinit var toolExecutor: ExecutorService
```

- [ ] **Step 2: Initialize thread pool in init() method**

In `Assistant.kt`, modify `init()` method at line 39 to add thread pool initialization:

```kotlin
fun init(config: Map<*, *>, context: Context) {
    maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
    description = MapUtil.toString("description", config) ?: ""
    coordinator = MapUtil.toBoolean("coordinator", config) ?: false
    maxDurationMinutes = MapUtil.toString("max-duration", config)
        ?.let { value -> DurationUtil.minutes(value, DEFAULT_MAX_DURATION_MINUTES) }
        ?: DEFAULT_MAX_DURATION_MINUTES
    
    // NEW: Initialize thread pool size
    threadPoolSize = MapUtil.toInt("thread-pool-size", config) ?: 4
    if (threadPoolSize < 2) {
        LOGGER.warn("thread-pool-size must be at least 2, using 2")
        threadPoolSize = 2
    }
    toolExecutor = Executors.newFixedThreadPool(threadPoolSize)

    this.context = context
    context.assistantRegistry.register(this)

    LOGGER.info("Assistant: $name")
    LOGGER.info("  coordinator: $coordinator")
    LOGGER.info("  max-duration: ${maxDurationMinutes}m")
    LOGGER.info("  max-iterations: $maxIterations")
    LOGGER.info("  thread-pool-size: $threadPoolSize")
}
```

- [ ] **Step 3: Add ExecutorService import**

In `Assistant.kt`, add import at line 16:

```kotlin
import java.util.concurrent.ExecutorService
```

- [ ] **Step 4: Update destroy() method to shutdown thread pool**

In `Assistant.kt`, modify `destroy()` at line 56:

```kotlin
fun destroy() {
    if (::toolExecutor.isInitialized) {
        LOGGER.info("Shutting down tool executor for assistant: $name")
        toolExecutor.shutdown()
        try {
            if (!toolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("Tool executor did not terminate in 30s, forcing shutdown")
                toolExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            LOGGER.warn("Interrupted while waiting for tool executor shutdown")
            toolExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
```

- [ ] **Step 5: Write test for thread pool initialization**

In `AssistantTest.kt`, add test after existing setup:

```kotlin
@Test
fun `init should configure thread pool size from config`() {
    val assistant = Assistant("test-pool")
    val config = mapOf("thread-pool-size" to 8)
    
    assistant.init(config, context)
    
    // Verify by checking logged value - thread pool size is private
    // Alternative: use reflection or add getter for testing
    assistant.destroy()
}

@Test
fun `init should enforce minimum thread pool size of 2`() {
    val assistant = Assistant("test-pool-min")
    val config = mapOf("thread-pool-size" to 1)
    
    assistant.init(config, context)
    
    // Should default to 2 (check logs or via reflection)
    assistant.destroy()
}

@Test
fun `destroy should shutdown thread pool gracefully`() {
    val assistant = Assistant("test-pool-destroy")
    assistant.init(emptyMap<String, Any>(), context)
    
    assistant.destroy()
    
    // Verify no exceptions thrown
}
```

- [ ] **Step 6: Run tests to verify configuration**

Run: `mvn test -Dtest=AssistantTest#init*`

Expected: 3 new tests PASS

- [ ] **Step 7: Commit thread pool configuration**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "feat: add thread pool configuration for parallel tool execution"
```

---

## Task 2: Create Parallel Tool Execution Result Model

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/ToolExecutionResult.kt`
- Test: `src/test/kotlin/com/wutsi/kokibot/ToolExecutionResultTest.kt`

- [ ] **Step 1: Write failing test for ToolExecutionResult data class**

Create `src/test/kotlin/com/wutsi/kokibot/ToolExecutionResultTest.kt`:

```kotlin
package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMToolCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToolExecutionResultTest {
    @Test
    fun `should create successful result`() {
        val call = LLMToolCall(name = "test-tool", id = "call-123")
        val result = ToolExecutionResult(
            call = call,
            result = "success output",
            error = null
        )
        
        assertEquals("test-tool", result.call.name)
        assertEquals("success output", result.result)
        assertNull(result.error)
    }
    
    @Test
    fun `should create error result`() {
        val call = LLMToolCall(name = "test-tool", id = "call-456")
        val error = Exception("tool failed")
        val result = ToolExecutionResult(
            call = call,
            result = "",
            error = error
        )
        
        assertEquals("test-tool", result.call.name)
        assertEquals("", result.result)
        assertEquals("tool failed", result.error?.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolExecutionResultTest`

Expected: FAIL with "ToolExecutionResult not found"

- [ ] **Step 3: Create ToolExecutionResult data class**

Create `src/main/kotlin/com/wutsi/kokibot/ToolExecutionResult.kt`:

```kotlin
package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMToolCall

/**
 * Result of a tool execution, used for parallel execution tracking
 */
data class ToolExecutionResult(
    val call: LLMToolCall,
    val result: String,
    val error: Exception? = null
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ToolExecutionResultTest`

Expected: PASS - 2 tests

- [ ] **Step 5: Commit result model**

```bash
git add src/main/kotlin/com/wutsi/kokibot/ToolExecutionResult.kt src/test/kotlin/com/wutsi/kokibot/ToolExecutionResultTest.kt
git commit -m "feat: add ToolExecutionResult model for parallel execution"
```

---

## Task 3: Refactor Tool Execution into Callable

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt:245-286`
- Test: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

- [ ] **Step 1: Write test for single tool execution as Callable**

In `AssistantTest.kt`, add test:

```kotlin
@Test
fun `should execute single tool call successfully`() {
    val query = Message(
        text = "test",
        userId = "user-1",
        channelId = "channel-1"
    )
    val call = LLMToolCall(
        name = "test-tool",
        arguments = mapOf("city" to "Paris"),
        id = "call-1"
    )
    
    val response1 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = null,
                toolCalls = listOf(call),
                finishReason = LLMFinishReason.TOOL_CALLS
            )
        )
    )
    val response2 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = "Final answer",
                finishReason = LLMFinishReason.STOP
            )
        )
    )
    
    doReturn(response1).doReturn(response2).whenever(llm).completion(any(), any())
    
    val result = assistant.process(query)
    
    assertEquals("Final answer", result.text)
    verify(tool1).exec(mapOf("city" to "Paris"))
}
```

- [ ] **Step 2: Run test to verify current behavior**

Run: `mvn test -Dtest=AssistantTest#should_execute_single_tool_call_successfully`

Expected: PASS (verifies baseline)

- [ ] **Step 3: Extract tool execution logic into Callable factory method**

In `Assistant.kt`, add new private method before line 245:

```kotlin
private fun createToolCallable(
    id: String,
    iteration: Int,
    call: LLMToolCall,
    tools: Map<String, Tool>
): Callable<ToolExecutionResult> {
    return Callable {
        LOGGER.info(
            "$iteration $name TOOL ${call.name} " +
                call.arguments.map { entry ->
                    "${entry.key}=" + entry.value?.let { value -> take(value.toString(), 200) }
                }.joinToString(",")
        )
        context.sessionLog.onToolUse(id, iteration, call)

        // Execute
        val tool = tools[call.name]
        val result = if (tool == null) {
            "The tool `${call.name}` is not available!"
        } else {
            try {
                tool.exec(call.arguments)
            } catch (e: Exception) {
                LOGGER.warn("Unexpected error while executing tool `${call.name}`. Error=${e.message}")
                "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
            }
        }

        ToolExecutionResult(call = call, result = result)
    }
}
```

- [ ] **Step 4: Add Callable import**

In `Assistant.kt`, add import at line 18:

```kotlin
import java.util.concurrent.Callable
```

- [ ] **Step 5: Run ktlint format**

Run: `mvn antrun:run@ktlint-format`

Expected: Formatting applied

- [ ] **Step 6: Run test to verify refactoring didn't break anything**

Run: `mvn test -Dtest=AssistantTest`

Expected: All tests PASS

- [ ] **Step 7: Commit callable extraction**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "refactor: extract tool execution into Callable for parallel execution"
```

---

## Task 4: Implement Parallel Tool Execution Logic

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt:216-231`
- Test: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

- [ ] **Step 1: Write test for parallel tool execution**

In `AssistantTest.kt`, add test:

```kotlin
@Test
fun `should execute multiple tool calls in parallel`() {
    val query = Message(
        text = "test parallel",
        userId = "user-1",
        channelId = "channel-1"
    )
    val call1 = LLMToolCall(name = "test-tool", arguments = mapOf("city" to "Paris"), id = "call-1")
    val call2 = LLMToolCall(name = "test-tool-2", arguments = mapOf("city" to "London"), id = "call-2")
    
    val response1 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = null,
                toolCalls = listOf(call1, call2),
                finishReason = LLMFinishReason.TOOL_CALLS
            )
        )
    )
    val response2 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = "Final answer",
                finishReason = LLMFinishReason.STOP
            )
        )
    )
    
    doReturn(response1).doReturn(response2).whenever(llm).completion(any(), any())
    
    val result = assistant.process(query)
    
    assertEquals("Final answer", result.text)
    verify(tool1).exec(mapOf("city" to "Paris"))
    verify(tool2).exec(mapOf("city" to "London"))
}
```

- [ ] **Step 2: Run test to verify it passes with current sequential logic**

Run: `mvn test -Dtest=AssistantTest#should_execute_multiple_tool_calls_in_parallel`

Expected: PASS (baseline with sequential execution)

- [ ] **Step 3: Replace decide() method with parallel execution logic**

In `Assistant.kt`, replace `decide()` method at line 216:

```kotlin
private fun decide(
    id: String,
    iteration: Int,
    response: LLMResponse,
    memory: MutableList<String>,
    tools: Map<String, Tool>,
): Boolean {
    // Collect all tool calls from all choices
    val allToolCalls = response.choices
        .flatMap { choice -> choice.toolCalls }
    
    if (allToolCalls.isEmpty()) {
        return true // No tool calls, done
    }
    
    // Execute all tool calls in parallel
    execParallel(id, iteration, allToolCalls, memory, tools)
    return false
}
```

- [ ] **Step 4: Implement execParallel() method**

In `Assistant.kt`, add new method after `decide()`:

```kotlin
private fun execParallel(
    id: String,
    iteration: Int,
    toolCalls: List<LLMToolCall>,
    memory: MutableList<String>,
    tools: Map<String, Tool>,
) {
    if (toolCalls.isEmpty()) {
        return
    }
    
    LOGGER.info("$iteration $name Executing ${toolCalls.size} tool calls in parallel")
    
    // Create callables for each tool call
    val callables = toolCalls.map { call ->
        createToolCallable(id, iteration, call, tools)
    }
    
    // Execute all in parallel and wait for completion
    val futures = callables.map { callable ->
        toolExecutor.submit(callable)
    }
    
    // Collect results (blocks until all complete)
    val results = futures.map { future ->
        try {
            future.get() // Blocks until this tool completes
        } catch (e: Exception) {
            LOGGER.error("Tool execution failed: ${e.message}", e)
            // Create error result
            ToolExecutionResult(
                call = LLMToolCall(name = "unknown", id = "error"),
                result = "",
                error = e
            )
        }
    }
    
    // Update memory with all results
    results.forEach { result ->
        memory.add(
            "Using tool `${result.call.name}` with arguments: " +
                result.call.arguments.map { entry ->
                    "${entry.key}=" + entry.value?.let { value ->
                        take(value.toString(), 200)
                    }
                }.joinToString(",")
        )
        memory.add(result.result)
        
        // Update session log
        context.sessionLog.onToolResult(id, iteration, result.call, result.result)
    }
    
    LOGGER.info("$iteration $name Completed ${results.size} tool calls")
}
```

- [ ] **Step 5: Add Future import**

In `Assistant.kt`, add import at line 17:

```kotlin
import java.util.concurrent.Future
```

- [ ] **Step 6: Remove old exec() methods that are now unused**

In `Assistant.kt`, delete these methods (lines 233-286 in original):
- `exec(id, iteration, choice, memory, tools)` 
- `exec(id, iteration, call, memory, tools)`

Keep only `exec(iteration, query, command)` for command execution.

- [ ] **Step 7: Run ktlint format**

Run: `mvn antrun:run@ktlint-format`

Expected: Formatting applied

- [ ] **Step 8: Run tests to verify parallel execution works**

Run: `mvn test -Dtest=AssistantTest`

Expected: All tests PASS

- [ ] **Step 9: Commit parallel execution implementation**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "feat: implement parallel tool execution with thread pool"
```

---

## Task 5: Add Error Handling and Timeout for Parallel Execution

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt` (execParallel method)
- Test: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

- [ ] **Step 1: Write test for tool execution timeout**

In `AssistantTest.kt`, add test:

```kotlin
@Test
fun `should handle tool execution timeout gracefully`() {
    val query = Message(
        text = "test timeout",
        userId = "user-1",
        channelId = "channel-1"
    )
    
    // Mock tool that hangs
    val slowTool = mock<Tool>()
    doReturn(ToolMetadata(name = "slow-tool", parameters = emptyList())).whenever(slowTool).metadata()
    doThrow(RuntimeException("Timeout")).whenever(slowTool).exec(any())
    
    val call = LLMToolCall(name = "slow-tool", id = "call-timeout")
    val response1 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = null,
                toolCalls = listOf(call),
                finishReason = LLMFinishReason.TOOL_CALLS
            )
        )
    )
    val response2 = LLMResponse(
        choices = listOf(
            LLMResponseChoice(
                content = "Handled error",
                finishReason = LLMFinishReason.STOP
            )
        )
    )
    
    doReturn(listOf(tool1, slowTool)).whenever(toolRegistry).all()
    doReturn(response1).doReturn(response2).whenever(llm).completion(any(), any())
    
    val result = assistant.process(query)
    
    assertEquals("Handled error", result.text)
    // Should not crash, should continue to next iteration
}
```

- [ ] **Step 2: Run test to verify it passes with current error handling**

Run: `mvn test -Dtest=AssistantTest#should_handle_tool_execution_timeout_gracefully`

Expected: PASS (current error handling should work)

- [ ] **Step 3: Add timeout configuration to tool execution**

In `Assistant.kt`, modify `createToolCallable()` to add timeout context:

```kotlin
private fun createToolCallable(
    id: String,
    iteration: Int,
    call: LLMToolCall,
    tools: Map<String, Tool>
): Callable<ToolExecutionResult> {
    return Callable {
        val startTime = System.currentTimeMillis()
        LOGGER.info(
            "$iteration $name TOOL ${call.name} " +
                call.arguments.map { entry ->
                    "${entry.key}=" + entry.value?.let { value -> take(value.toString(), 200) }
                }.joinToString(",")
        )
        context.sessionLog.onToolUse(id, iteration, call)

        // Execute
        val tool = tools[call.name]
        val result = if (tool == null) {
            "The tool `${call.name}` is not available!"
        } else {
            try {
                val output = tool.exec(call.arguments)
                val duration = System.currentTimeMillis() - startTime
                LOGGER.info("$iteration $name TOOL ${call.name} completed in ${duration}ms")
                output
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                LOGGER.warn("Unexpected error while executing tool `${call.name}` after ${duration}ms. Error=${e.message}")
                "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
            }
        }

        ToolExecutionResult(call = call, result = result)
    }
}
```

- [ ] **Step 4: Improve error handling in execParallel() for failed futures**

In `Assistant.kt`, update `execParallel()` result collection:

```kotlin
// Collect results (blocks until all complete)
val results = futures.mapIndexed { index, future ->
    try {
        future.get() // Blocks until this tool completes
    } catch (e: Exception) {
        val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
        LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
        // Create error result
        val errorMessage = when (e) {
            is java.util.concurrent.TimeoutException -> 
                "Tool `${call.name}` timed out"
            is java.util.concurrent.CancellationException -> 
                "Tool `${call.name}` was cancelled"
            else -> 
                "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
        }
        ToolExecutionResult(
            call = call,
            result = errorMessage,
            error = e
        )
    }
}
```

- [ ] **Step 5: Run ktlint format**

Run: `mvn antrun:run@ktlint-format`

Expected: Formatting applied

- [ ] **Step 6: Run tests to verify error handling**

Run: `mvn test -Dtest=AssistantTest`

Expected: All tests PASS

- [ ] **Step 7: Commit error handling improvements**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "feat: add improved error handling and logging for parallel tool execution"
```

---

## Task 6: Add Integration Test for Parallel Execution Performance

**Files:**
- Create: `src/test/kotlin/com/wutsi/kokibot/ParallelToolExecutionIntegrationTest.kt`

- [ ] **Step 1: Write integration test verifying parallel speedup**

Create `src/test/kotlin/com/wutsi/kokibot/ParallelToolExecutionIntegrationTest.kt`:

```kotlin
package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.io.File

class ParallelToolExecutionIntegrationTest {
    private lateinit var assistant: Assistant
    private lateinit var context: Context
    private val toolRegistry = mock<ToolRegistry>()
    private val llm = mock<LLM>()
    
    @BeforeEach
    fun setup() {
        // Create slow tools that simulate network calls
        val slowTool1 = createSlowTool("weather-tool", delayMs = 500)
        val slowTool2 = createSlowTool("news-tool", delayMs = 500)
        val slowTool3 = createSlowTool("stock-tool", delayMs = 500)
        
        doReturn(listOf(slowTool1, slowTool2, slowTool3)).whenever(toolRegistry).all()
        
        context = Context(
            home = File(System.getProperty("java.io.tmpdir"), "kokibot-test-parallel"),
            llm = llm,
            toolRegistry = toolRegistry,
            memory = mock<Memory>(),
            commandRegistry = mock<CommandRegistry>(),
            skillRegistry = mock<SkillRegistry>(),
            dailyLog = mock<DailyLog>(),
            sessionLog = mock<SessionLog>(),
            chatHistory = mock<ChatHistory>(),
            assistantRegistry = mock<AssistantRegistry>(),
        )
        
        assistant = Assistant("test-parallel")
        assistant.init(mapOf("thread-pool-size" to 4), context)
    }
    
    @AfterEach
    fun tearDown() {
        assistant.destroy()
    }
    
    @Test
    fun `parallel execution should be faster than sequential would be`() {
        val query = Message(text = "test parallel perf", userId = "user-1", channelId = "channel-1")
        
        // LLM returns 3 tool calls
        val response1 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = null,
                    toolCalls = listOf(
                        LLMToolCall(name = "weather-tool", id = "call-1"),
                        LLMToolCall(name = "news-tool", id = "call-2"),
                        LLMToolCall(name = "stock-tool", id = "call-3")
                    ),
                    finishReason = LLMFinishReason.TOOL_CALLS
                )
            )
        )
        val response2 = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "All results collected",
                    finishReason = LLMFinishReason.STOP
                )
            )
        )
        
        doReturn(response1).doReturn(response2).whenever(llm).completion(any(), any())
        
        val startTime = System.currentTimeMillis()
        val result = assistant.process(query)
        val duration = System.currentTimeMillis() - startTime
        
        assertEquals("All results collected", result.text)
        
        // 3 tools × 500ms = 1500ms sequentially
        // With parallel execution (3 threads): ~500ms + overhead
        // Should complete in less than 1000ms
        assertTrue(duration < 1000, "Parallel execution took ${duration}ms, expected < 1000ms")
    }
    
    private fun createSlowTool(name: String, delayMs: Long): Tool {
        val tool = mock<Tool>()
        doReturn(ToolMetadata(name = name, parameters = emptyList())).whenever(tool).metadata()
        doAnswer { invocation ->
            Thread.sleep(delayMs)
            "Result from $name"
        }.whenever(tool).exec(any())
        return tool
    }
}
```

- [ ] **Step 2: Run test to verify parallel speedup**

Run: `mvn test -Dtest=ParallelToolExecutionIntegrationTest`

Expected: PASS - execution time < 1000ms (vs 1500ms sequential)

- [ ] **Step 3: Commit integration test**

```bash
git add src/test/kotlin/com/wutsi/kokibot/ParallelToolExecutionIntegrationTest.kt
git commit -m "test: add integration test verifying parallel execution performance"
```

---

## Task 7: Update Documentation

**Files:**
- Modify: `CLAUDE.md` (Configuration section)

- [ ] **Step 1: Update CLAUDE.md with thread pool configuration**

In `CLAUDE.md`, find the "Configuration Options" section and add:

```markdown
- `assistant.thread-pool-size` (integer): Thread pool size for parallel tool execution (default: 4, min: 2)
```

Update the example configuration to include:

```json
{
    "assistant": {
        "coordinator": false,
        "max-iterations": 10,
        "max-duration": "5m",
        "thread-pool-size": 4,
        "description": "General purpose assistant"
    },
    ...
}
```

Add a new section "Parallel Tool Execution" under "Core Request Flow":

```markdown
### Parallel Tool Execution

The assistant executes independent tool calls in parallel to reduce response time:

1. **LLM** returns response with multiple tool calls
2. **Assistant** collects all tool calls from all choices
3. **Thread Pool** submits all tool calls as concurrent tasks
4. **Assistant** blocks until all tool calls complete
5. Results are added to iteration memory in order
6. Loop continues with next LLM call

**Configuration:**
- `assistant.thread-pool-size`: Controls concurrency (default: 4 threads)
- Minimum 2 threads enforced
- Thread pool gracefully shuts down with assistant

**Error Handling:**
- Individual tool failures don't block other tools
- Errors are logged and returned as tool results
- Timeouts and cancellations are handled gracefully
```

- [ ] **Step 2: Commit documentation updates**

```bash
git add CLAUDE.md
git commit -m "docs: add parallel tool execution documentation"
```

---

## Task 8: Run Full Test Suite and Format

**Files:**
- All source files

- [ ] **Step 1: Run ktlint format on entire codebase**

Run: `mvn antrun:run@ktlint-format`

Expected: All files formatted

- [ ] **Step 2: Run full test suite**

Run: `mvn test`

Expected: All tests PASS

- [ ] **Step 3: Verify coverage meets requirements**

Run: `mvn test jacoco:report`

Expected: Line coverage ≥ 90%, Class coverage ≥ 90%

- [ ] **Step 4: View coverage report**

Run: `open target/site/jacoco/index.html`

Review: Verify new classes (ToolExecutionResult, parallel execution logic) are well covered

- [ ] **Step 5: Run full build with integration tests**

Run: `mvn clean install`

Expected: BUILD SUCCESS

- [ ] **Step 6: Final commit if any formatting changes**

```bash
git add -A
git commit -m "chore: apply ktlint formatting"
```

---

## Verification Checklist

After completing all tasks, verify:

- [ ] Assistant initializes thread pool from configuration
- [ ] Thread pool size defaults to 4, minimum enforced as 2
- [ ] Thread pool shuts down gracefully on destroy
- [ ] Multiple tool calls execute in parallel
- [ ] Single tool call still works correctly
- [ ] Tool execution errors are handled gracefully
- [ ] Session log records all tool calls and results
- [ ] Memory is updated with all tool results after parallel execution completes
- [ ] Integration test shows parallel execution is faster than sequential
- [ ] All tests pass: `mvn test`
- [ ] Coverage requirements met: ≥90% line and class coverage
- [ ] Full build succeeds: `mvn clean install`
- [ ] Documentation updated in CLAUDE.md

---

## Rollback Plan

If issues arise:

1. **Thread pool initialization failures**: Check minimum size enforcement, verify config parsing
2. **Deadlocks or hangs**: Verify futures are collected with timeout, check tool implementations
3. **Memory corruption**: Ensure memory updates happen after all results collected, check thread safety
4. **Performance regression**: Compare integration test results, check thread pool size configuration
5. **Full rollback**: `git revert` commits in reverse order

---

## Future Enhancements (Out of Scope)

- Per-tool timeout configuration
- Dependency analysis to detect tool call ordering requirements
- Adaptive thread pool sizing based on tool execution patterns
- Tool execution metrics and performance monitoring
- Circuit breaker pattern for frequently failing tools
