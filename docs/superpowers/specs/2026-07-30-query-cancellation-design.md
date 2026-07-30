# Design: Query Cancellation

**Date:** 2026-07-30
**Status:** Approved

## Goal

Let an external caller cancel an in-flight assistant query via a REST call, keyed by the query's `id`. The reasoning
loop checks for cancellation cooperatively between iterations — no thread interruption, no changes to the LLM HTTP
layer or `ToolOrchestrator`.

## Background

`Assistant.process()` already has a hard-cancel path (`future.cancel(true)` on `max-duration` timeout,
`Assistant.kt:222-224`), but nothing lets an external caller trigger cancellation for a specific request, and
`ReActReasoningLoop.execute()`'s `while(true)` loop (`ReActReasoningLoop.kt:56`) has no cancellation checkpoint at
all — only a `maxIterations` bound.

`query.id` (== `InboxMessage.id`) is already the durable per-request key threaded through `Inbox`, `SessionLog`, and
`ExecutionContext.sessionId`, so it's the natural key for a cancellation signal.

Rejected alternative: an in-memory `ConcurrentHashMap<String, AtomicBoolean>` registry. Works, but doesn't survive a
process restart and adds another piece of shared mutable state. A file-based marker reuses `Inbox`'s existing
state-directory pattern (`pending/processing/done/failed/orphaned`) and needs no new registry.

## Changes

### 1. Cancel marker — `Inbox.kt`

Add a `cancel` directory alongside the existing state dirs, created in `init()`:

```kotlin
const val CANCEL = "cancel"
...
listOf(PENDING, PROCESSING, DONE, FAILED, ORPHANED, CANCEL).forEach { state ->
    File(inboxDir, state).mkdirs()
}
```

New methods:

```kotlin
fun cancel(id: String) {
    File(File(inboxDir, CANCEL), "$id.cancel").createNewFile()
    LOGGER.info("Cancel requested for $id")
}

fun isCancelled(id: String): Boolean =
    File(File(inboxDir, CANCEL), "$id.cancel").exists()

private fun clearCancel(id: String) {
    File(File(inboxDir, CANCEL), "$id.cancel").delete()
}
```

`cancel()` always creates the marker — no lookup against `PENDING`/`PROCESSING` first. If the id never existed or has
already finished, the marker is harmless and swept up next time cleanup runs (see below).

`complete()` and `fail()` both call `clearCancel(id)` before returning, so every terminal transition removes its own
marker. No other call site needs to know about cancellation markers.

### 2. Reasoning loop checkpoint — `ReActReasoningLoop.kt`

At the top of `execute()`'s `while(true)` loop (`ReActReasoningLoop.kt:56`), alongside the existing `maxIterations`
check:

```kotlin
while (true) {
    if (iteration++ > maxIterations) {
        throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
    }
    if (context.inbox.isCancelled(query.id)) {
        return Message(
            text = "Query cancelled.",
            role = Role.ASSISTANT,
            finishReason = FinishReason.CANCELLED,
        )
    }
    ...
}
```

This is a best-effort checkpoint only: an LLM call or tool batch already in flight for the current iteration
completes normally. Cancellation takes effect before the *next* iteration starts. A single non-streaming LLM call
that never returns will delay cancellation until it does — this is an accepted limitation, not a bug to work around
here.

### 3. New finish reason — `FinishReason.kt`

```kotlin
enum class FinishReason {
    UNKNOWN,
    DONE,
    TOO_MANY_ITERATIONS,
    FAILURE,
    TIMEOUT,
    CANCELLED,
}
```

### 4. REST trigger — new `QueryCancelController`

```kotlin
@RestController
@RequestMapping("/assistants")
class QueryCancelController(private val multi: MultiBootstrap) {

    @PostMapping("/{name}/queries/{id}/cancel")
    fun cancel(
        @PathVariable name: String,
        @PathVariable id: String,
    ): ResponseEntity<Any> {
        val context = multi.bootstraps
            .firstOrNull { it.getContext().assistant.name == name }
            ?.getContext()
            ?: return ResponseEntity.notFound().build()

        context.inbox.cancel(id)
        return ResponseEntity.ok().build()
    }
}
```

Same assistant-lookup pattern as `ConversationController`. Returns 404 only if `name` doesn't resolve to a known
assistant. Returns 200 for any `id`, whether or not it's currently in-flight — matches `Inbox.cancel()`'s
always-write behavior.

## Files Changed

| File | Change |
|------|--------|
| `Inbox.kt` | New `CANCEL` state dir; `cancel()`, `isCancelled()`, `clearCancel()`; `complete()`/`fail()` call `clearCancel()` |
| `ReActReasoningLoop.kt` | Cancellation check at top of the reasoning loop |
| `FinishReason.kt` | New `CANCELLED` value |
| `controller/QueryCancelController.kt` | New — `POST /assistants/{name}/queries/{id}/cancel` |

## What Does Not Change

- `ToolOrchestrator`, `DeepseekClient`, `Kimi`, `Gemini` — no changes; no attempt to interrupt an in-flight LLM call
  or tool execution
- `Assistant.kt`'s existing `future.cancel(true)` timeout path — unrelated, left as-is
- `ExecutionContext` — not used for cancellation; the marker file is the single source of truth

## Testing

- `InboxTest`: `cancel()` creates the marker; `isCancelled()` reflects marker presence/absence; `complete()` and
  `fail()` clear an existing marker
- `ReActReasoningLoopTest`: loop returns a `CANCELLED` message and does not start another iteration when the marker
  is present at the top of the loop; loop proceeds normally when no marker exists
- `QueryCancelControllerTest`: 200 + marker file created for a known assistant name; 404 for an unknown assistant
  name; 200 even when `id` doesn't correspond to any in-flight request
