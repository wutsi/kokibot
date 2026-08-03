# Query Cancellation via Exception Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current "return a CANCELLED `Message` from the reasoning loop" cancellation handling with a `QueryCancelledException` that is thrown as soon as cancellation is detected — including mid-tool-execution — and caught centrally in `Assistant`.

**Architecture:** Introduce one new unchecked exception, `QueryCancelledException`. `ReActReasoningLoop` throws it (instead of returning a `Message`) when `context.inbox.isCancelled(query.id)` is true at the top of each iteration. `ToolOrchestrator` gains two cancellation checks: one before dispatching a batch of tool calls, and one inside each tool's `Callable` right before it runs — the latter catches cancellation that happens while a tool is still queued behind others on the shared thread pool. `Assistant.doProcessAsync` catches `QueryCancelledException` the same way it already catches `TooManyIterationException`, mapping it to a `Message` with `FinishReason.CANCELLED`. LLM calls in flight are explicitly out of scope — cancellation is only checked between iterations and between/before tool executions, not during a blocking LLM HTTP call.

**Tech Stack:** Kotlin, JUnit 5, mockito-kotlin (`com.nhaarman.mockitokotlin2`).

## Global Constraints

- Run `mvn antrun:run@ktlint-format` before considering any task done.
- Keep the cancelled-message text as `"Query cancelled."` — this is user-visible copy already relied on by `ReActReasoningLoopTest`.
- Do not touch LLM call cancellation — explicitly out of scope per this plan.
- 90% line/class coverage is enforced by the build; every new branch needs a covering test.

---

### Task 1: Add `QueryCancelledException` and throw it from `ReActReasoningLoop`

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/QueryCancelledException.kt`
- Modify: `src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt:60-66`
- Modify (test): `src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt:165-177`

**Interfaces:**
- Produces: `class QueryCancelledException(message: String = "Query cancelled.") : RuntimeException(message)` — used by Task 2 (`ToolOrchestrator`) and Task 3 (`Assistant`).

- [ ] **Step 1: Write the failing test**

Replace the existing cancellation test in `ReActReasoningLoopTest.kt` (it currently asserts a returned `Message`; change it to assert a thrown exception):

```kotlin
    @Test
    fun `should throw QueryCancelledException when query is cancelled`() {
        val query = Message(id = "query-1", text = "Test", userId = "user1", channelId = "channel1")
        val memory = mutableListOf<String>()
        doReturn(true).whenever(inbox).isCancelled("query-1")

        assertThrows<QueryCancelledException> {
            reasoningLoop.execute(query, null, 0, memory, context)
        }
        verify(llm, times(0)).completion(any(), any())
    }
```

Add the import next to the other top-level exception import:

```kotlin
import com.wutsi.kokibot.QueryCancelledException
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ReActReasoningLoopTest#should_throw_QueryCancelledException_when_query_is_cancelled`
Expected: FAIL — compile error, `QueryCancelledException` does not exist yet.

- [ ] **Step 3: Create the exception**

```kotlin
package com.wutsi.kokibot

class QueryCancelledException(message: String = "Query cancelled.") : RuntimeException(message)
```

- [ ] **Step 4: Throw it from `ReActReasoningLoop`**

In `src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt`, replace:

```kotlin
            if (context.inbox.isCancelled(query.id)) {
                return Message(
                    text = "Query cancelled.",
                    role = Role.ASSISTANT,
                    finishReason = FinishReason.CANCELLED,
                )
            }
```

with:

```kotlin
            if (context.inbox.isCancelled(query.id)) {
                throw QueryCancelledException()
            }
```

Add the import: `import com.wutsi.kokibot.QueryCancelledException` (same package as `Message`/`Role`/`FinishReason`, which are already imported without a package prefix in this file since `ReActReasoningLoop` lives in `com.wutsi.kokibot.assistant` — check existing imports for `TooManyIterationException` at the top of the file for the exact style to match).

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ReActReasoningLoopTest`
Expected: PASS — all tests in the file pass, including the rewritten cancellation test.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/QueryCancelledException.kt \
        src/main/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoop.kt \
        src/test/kotlin/com/wutsi/kokibot/assistant/ReActReasoningLoopTest.kt
git commit -m "Throw QueryCancelledException instead of returning a cancelled Message from ReActReasoningLoop"
```

---

### Task 2: Check cancellation in `ToolOrchestrator` before and during tool execution

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt`
- Modify (test): `src/test/kotlin/com/wutsi/kokibot/assistant/ToolOrchestratorTest.kt`

**Interfaces:**
- Consumes: `QueryCancelledException` from Task 1; `context.inbox.isCancelled(query.id): Boolean` (existing, `com.wutsi.kokibot.service.inbox.Inbox`).
- Produces: `ToolOrchestrator.executeTools(...)` now throws `QueryCancelledException` (in addition to its existing behavior) when cancellation is detected before or during tool execution. This is consumed by `ReActReasoningLoop.decide()` (no change needed there — the exception just propagates) and, ultimately, caught in Task 3.

Two checks are added:
1. **Batch-level** — at the top of `executeTools`, before dispatching either the single-tool or parallel path. Catches cancellation that already happened before this call.
2. **Per-tool** — inside `createToolCallable`'s `Callable`, right before `tool.exec(...)` runs. Catches cancellation that happens while earlier tools in the same batch are still running and this tool is still queued on the shared thread pool (`threadPoolSize` may be smaller than `toolCalls.size`). This check covers both the single-tool path (`single()` calls the same callable directly) and the parallel path.

Because the per-tool check runs inside a `Callable` submitted to `toolExecutor`, an exception it throws is wrapped in `java.util.concurrent.ExecutionException` when the parallel path calls `future.get()`. That must be unwrapped and re-thrown as `QueryCancelledException`, not converted into an error `ToolExecutionResult` like other tool failures.

- [ ] **Step 1: Write the failing tests**

Add `inbox` stubbing to the existing `ToolOrchestratorTest` setup (needed for the new checks to have a defined default), and two new tests. First, update imports and fields:

```kotlin
import com.wutsi.kokibot.QueryCancelledException
import com.wutsi.kokibot.service.inbox.Inbox
import org.junit.jupiter.api.assertThrows
```

```kotlin
class ToolOrchestratorTest {
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val toolRegistry = mock<ToolRegistry>()
    private val sessionLog = mock<SessionLog>()
    private val channelRegistry = mock<ChannelRegistry>()
    private val inbox = mock<Inbox>()
    private val context = mock<Context>()
    private lateinit var orchestrator: ToolOrchestrator

    @BeforeEach
    fun setup() {
        doReturn(sessionLog).whenever(context).sessionLog
        doReturn(toolRegistry).whenever(context).toolRegistry
        doReturn(channelRegistry).whenever(context).channelRegistry
        doReturn(inbox).whenever(context).inbox
        doReturn(false).whenever(inbox).isCancelled(any())

        // ... rest of existing setup unchanged ...
```

Add two new test methods at the end of the class:

```kotlin
    @Test
    fun `should throw QueryCancelledException when already cancelled before dispatch`() {
        doReturn(true).whenever(inbox).isCancelled("test-id")

        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        assertThrows<QueryCancelledException> {
            orchestrator.executeTools(
                id = query.id,
                iteration = 1,
                assistantName = "test-assistant",
                toolCalls = toolCalls,
                memory = memory,
                tools = tools,
                query = query,
                context = context
            )
        }
        verify(tool1, org.mockito.kotlin.never()).exec(any())
    }

    @Test
    fun `should throw QueryCancelledException for a tool queued behind a cancelling one`() {
        // threadPoolSize = 1 forces tool2's callable to wait behind tool1's on the shared executor,
        // so by the time tool2 runs, cancellation (flipped as a side effect of tool1.exec) is visible.
        orchestrator.destroy()
        orchestrator = ToolOrchestrator(threadPoolSize = 1)

        doReturn("result1").whenever(tool1).exec(any())
        whenever(tool1.exec(any())).then {
            doReturn(true).whenever(inbox).isCancelled("test-id")
            "result1"
        }

        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1")),
            LLMToolCall(id = "2", name = "tool2", arguments = mapOf("arg2" to "value2"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1, "tool2" to tool2)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        assertThrows<QueryCancelledException> {
            orchestrator.executeTools(
                id = query.id,
                iteration = 1,
                assistantName = "test-assistant",
                toolCalls = toolCalls,
                memory = memory,
                tools = tools,
                query = query,
                context = context
            )
        }
        verify(tool2, org.mockito.kotlin.never()).exec(any())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: FAIL — both new tests fail because no cancellation checks exist yet (tool1/tool2 execute normally, no exception thrown), and the `never()` verifications fail.

- [ ] **Step 3: Add the batch-level check**

In `src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt`, add the import:

```kotlin
import com.wutsi.kokibot.QueryCancelledException
import java.util.concurrent.ExecutionException
```

Update `executeTools`:

```kotlin
    fun executeTools(
        id: String,
        iteration: Int,
        assistantName: String,
        toolCalls: List<LLMToolCall>,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ) {
        if (toolCalls.isEmpty()) {
            return
        }
        if (context.inbox.isCancelled(query.id)) {
            throw QueryCancelledException()
        }

        // ... rest unchanged (single/parallel dispatch + memory update loop) ...
```

- [ ] **Step 4: Add the per-tool check inside the callable**

Update `createToolCallable`:

```kotlin
    private fun createToolCallable(
        id: String,
        iteration: Int,
        assistantName: String,
        call: LLMToolCall,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): Callable<ToolExecutionResult> {
        return Callable {
            ExecutionContext.set(id, assistantName, query.userId, query.channelId)
            try {
                if (context.inbox.isCancelled(query.id)) {
                    throw QueryCancelledException()
                }

                val startTime = System.currentTimeMillis()
                // ... rest of the existing body unchanged ...
```

- [ ] **Step 5: Unwrap `QueryCancelledException` in the parallel path**

Update the `catch` block in `parallel()` so cancellation is re-thrown instead of turned into an error result. Add a dedicated `catch` for `ExecutionException` before the existing generic `catch (e: Exception)`:

```kotlin
        return futures.mapIndexed { index, future ->
            try {
                future.get()
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is QueryCancelledException) {
                    throw cause
                }
                val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
                LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
                ToolExecutionResult(
                    call = call,
                    result = "Unexpected error while executing tool `${call.name}`. Error=${cause?.message ?: e.message}",
                    error = e
                )
            } catch (e: Exception) {
                val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
                LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
                val errorMessage = when (e) {
                    is TimeoutException -> "Tool `${call.name}` timed out"
                    is CancellationException -> "Tool `${call.name}` was cancelled"
                    else -> "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
                ToolExecutionResult(call = call, result = errorMessage, error = e)
            }
        }
```

Note: `single()` calls `callable.call()` directly (no executor), so `QueryCancelledException` thrown inside it is never wrapped and propagates on its own — no change needed there.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: PASS — all existing and new tests pass. Double check `should handle tool errors gracefully` still passes (a plain `RuntimeException` from a tool must still become an error `ToolExecutionResult`, not propagate).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt \
        src/test/kotlin/com/wutsi/kokibot/assistant/ToolOrchestratorTest.kt
git commit -m "Throw QueryCancelledException from ToolOrchestrator before and during tool execution"
```

---

### Task 3: Handle `QueryCancelledException` in `Assistant.doProcessAsync`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt:258-267`
- Modify (test): `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

**Interfaces:**
- Consumes: `QueryCancelledException` from Task 1, now thrown by `ReActReasoningLoop` (Task 1) and `ToolOrchestrator` (Task 2).
- Produces: `Assistant.process(query)` returns `Message(text = "Query cancelled.", role = Role.ASSISTANT, finishReason = FinishReason.CANCELLED)` when the query was cancelled — same externally-visible contract as before this refactor, just reached via exception instead of a direct return from `ReActReasoningLoop`.

- [ ] **Step 1: Write the failing test**

Add to `AssistantTest.kt`. This is an end-to-end test through the real `ReActReasoningLoop`/`ToolOrchestrator`/`Inbox` wired up in `setup()` — it marks the query cancelled via the real `Inbox` before calling `process`:

```kotlin
    @Test
    fun `process cancelled query`() {
        // GIVEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        context.inbox.cancel(prompt.id)

        // WHEN
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Query cancelled.", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.CANCELLED, result.finishReason)
        verify(llm, times(0)).completion(any(), any())
    }
```

Note: `Message.id` defaults to a fresh random UUID per instance (see `Message.kt`), so `prompt.id` here is the exact id `Assistant.process` will look up — no need to pass an explicit `id`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AssistantTest#process_cancelled_query`
Expected: FAIL — currently `QueryCancelledException` (once Tasks 1–2 land) propagates out of `doProcessAsync`'s generic `catch (e: Exception)` branch, producing `FinishReason.FAILURE` with `ERROR_FAILURE` text instead of `FinishReason.CANCELLED`.

- [ ] **Step 3: Add the dedicated catch block**

In `src/main/kotlin/com/wutsi/kokibot/Assistant.kt`, update `doProcessAsync`:

```kotlin
    private fun doProcessAsync(
        query: Message,
        streamCallback: ((LLMStreamData) -> Unit)? = null,
        iteration: Int,
        memory: MutableList<String>,
    ): Message {
        return try {
            reasoningLoop.execute(query, streamCallback, iteration, memory, context)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(ERROR_TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: QueryCancelledException) {
            LOGGER.info("Query cancelled: ${query.id}")
            Message(e.message ?: "Query cancelled.", Role.ASSISTANT, FinishReason.CANCELLED)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }
    }
```

`QueryCancelledException` is in the same package (`com.wutsi.kokibot`) as `Assistant`, so no new import is needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AssistantTest`
Expected: PASS — new test passes, and all pre-existing `AssistantTest` tests still pass.

- [ ] **Step 5: Run the full test suite and linter**

Run: `mvn antrun:run@ktlint-format && mvn clean install`
Expected: PASS — full build green, including the 90% coverage gates (the new `catch` branch and the two new `ToolOrchestrator` checks each need their tests counted; re-check `target/site/jacoco/index.html` for `Assistant.kt`, `ReActReasoningLoop.kt`, and `ToolOrchestrator.kt` if coverage fails).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt \
        src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "Handle QueryCancelledException in Assistant and map it to a CANCELLED response"
```
