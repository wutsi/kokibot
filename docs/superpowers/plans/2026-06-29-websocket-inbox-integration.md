# WebSocketChannel + Inbox Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate `WebSocketChannel` with the `Inbox` queue so WebSocket messages are processed by `InboxPoller` (with WIP limiting and per-session routing) instead of inline, while preserving full streaming parity for clients.

**Architecture:** `WebSocketChannel.handleMessage()` submits to `Inbox` using `session.id` as `userId`, mirroring `TelegramChannel`. `InboxPoller` processes queued messages and routes `REASONING_CHUNK` frames (with `usage`) and `FINAL` frames (with `conversationId`) back to the correct open WebSocket session via `channel.sendStatus()` / `channel.send()`.

**Tech Stack:** Kotlin 2.3.21, Spring WebSocket, JUnit 5, Mockito-Kotlin

## Global Constraints

- Run `mvn antrun:run@ktlint-format` before every commit — ktlint violations fail the build
- Line coverage ≥ 90%, class coverage ≥ 90% (JaCoCo enforced at `mvn install`)
- Test pattern: `mock<Type>()` and `whenever(...).doReturn(...)` from `com.nhaarman.mockitokotlin2`
- `Message` is in package `com.wutsi.kokibot`; `LLMUsage` is in `com.wutsi.kokibot.llm`
- `WebSocketChannel.id()` returns `"channel:websocket"` (inherited from `Channel.id() = "channel:${name()}"`)
- `Inbox.submit(message: Message)` takes a `Message` and creates an `InboxMessage` internally

---

### Task 1: Add `usage` field to `Message`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Message.kt`
- Test: `src/test/kotlin/com/wutsi/kokibot/MessageTest.kt` (create if absent)

**Interfaces:**
- Produces: `Message.usage: LLMUsage?` — used by Tasks 2 and 3

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/wutsi/kokibot/MessageTest.kt`:

```kotlin
package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLMUsage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageTest {
    @Test
    fun `usage defaults to null`() {
        val message = Message(text = "hello")
        assertNull(message.usage)
    }

    @Test
    fun `usage is preserved when set`() {
        val usage = LLMUsage(totalTokens = 30, promptTokens = 10, completionTokens = 20)
        val message = Message(text = "hello", usage = usage)
        assertEquals(usage, message.usage)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -Dtest=MessageTest
```

Expected: FAIL — `usage` parameter does not exist on `Message`

- [ ] **Step 3: Add `usage` field to `Message`**

In `src/main/kotlin/com/wutsi/kokibot/Message.kt`, add one field:

```kotlin
import com.wutsi.kokibot.llm.LLMUsage

data class Message(
    val text: String = "",
    val role: Role = Role.UNKNOWN,
    val finishReason: FinishReason = FinishReason.UNKNOWN,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val channelId: String? = null,
    val userId: String? = null,
    val filePaths: List<String> = emptyList(),
    val id: String = UUID.randomUUID().toString(),
    val subject: String? = null,
    val conversationId: String? = null,
    val usage: LLMUsage? = null,
)
```

- [ ] **Step 4: Run tests**

```bash
mvn test -Dtest=MessageTest
```

Expected: PASS (both tests green)

- [ ] **Step 5: Run full suite to confirm no regressions**

```bash
mvn test
```

Expected: All tests pass (the field defaults to `null`, so no existing constructor calls break)

- [ ] **Step 6: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/Message.kt \
        src/test/kotlin/com/wutsi/kokibot/MessageTest.kt
git commit -m "feat: add usage field to Message for streaming token forwarding"
```

---

### Task 2: Forward `usage` and `conversationId` in `InboxPoller`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/service/inbox/InboxPoller.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/service/inbox/InboxPollerTest.kt`

**Interfaces:**
- Consumes: `Message.usage: LLMUsage?` (from Task 1), `LLMStreamData.usage: LLMUsage?` (existing)
- Produces: `channel.sendStatus()` called with `message.usage` set; `channel.send()` called with `message.conversationId` set

- [ ] **Step 1: Write the failing tests**

In `InboxPollerTest.kt`, update the existing test `tick - forwards stream data to channel sendStatus` and add a new test.

**Update** `tick - forwards stream data to channel sendStatus` — rename and extend it to also assert `usage`:

```kotlin
@Test
fun `tick - forwards stream data and usage to channel sendStatus`() {
    val channel = mock<Channel>()
    doReturn(channel).whenever(channelRegistry).get("channel:telegram")
    val usage = LLMUsage(totalTokens = 30, promptTokens = 10, completionTokens = 20)
    doAnswer { invocation ->
        val callback = invocation.getArgument<((LLMStreamData) -> Unit)?>(1)
        callback?.invoke(LLMStreamData(text = "partial...", usage = usage))
        Message(text = "full response", role = Role.ASSISTANT)
    }.whenever(context.assistant).process(any(), anyOrNull())

    inbox.submit(message("msg-1", channelId = "channel:telegram"))

    poller.tick()

    val statusCaptor = argumentCaptor<Message>()
    verify(channel).sendStatus(statusCaptor.capture())
    assertEquals("partial...", statusCaptor.firstValue.text)
    assertEquals(usage, statusCaptor.firstValue.usage)
    assertEquals(Role.ASSISTANT, statusCaptor.firstValue.role)
}
```

**Add** new test after it:

```kotlin
@Test
fun `tick - forwards conversationId in delivered response`() {
    val channel = mock<Channel>()
    doReturn(channel).whenever(channelRegistry).get("channel:telegram")
    doReturn(Message(text = "reply", role = Role.ASSISTANT, conversationId = "conv-999"))
        .whenever(context.assistant).process(any(), anyOrNull())

    inbox.submit(message("msg-1", channelId = "channel:telegram"))

    poller.tick()

    val responseCaptor = argumentCaptor<Message>()
    verify(channel).send(responseCaptor.capture())
    assertEquals("conv-999", responseCaptor.firstValue.conversationId)
}
```

Also add `import com.wutsi.kokibot.llm.LLMUsage` to the test file imports.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=InboxPollerTest
```

Expected: `tick - forwards stream data and usage to channel sendStatus` FAIL (usage not set); `tick - forwards conversationId in delivered response` FAIL (conversationId not forwarded)

- [ ] **Step 3: Update `InboxPoller.streamCallback()` to forward usage**

In `src/main/kotlin/com/wutsi/kokibot/service/inbox/InboxPoller.kt`, update `streamCallback`:

```kotlin
private fun streamCallback(inboxMessage: InboxMessage, channel: Channel?): ((LLMStreamData) -> Unit)? {
    channel ?: return null
    return { data ->
        try {
            channel.sendStatus(
                Message(
                    channelId = inboxMessage.channelId,
                    userId = inboxMessage.userId,
                    text = data.text,
                    usage = data.usage,
                    role = Role.ASSISTANT,
                )
            )
        } catch (e: Exception) {
            LOGGER.warn("Failed to send stream update for ${inboxMessage.id}: ${e.message}")
        }
    }
}
```

- [ ] **Step 4: Update `InboxPoller.deliver()` to forward conversationId**

In the same file, update `deliver`:

```kotlin
private fun deliver(inboxMessage: InboxMessage, response: Message, channel: Channel?) {
    channel ?: return
    try {
        channel.send(
            Message(
                channelId = inboxMessage.channelId,
                userId = inboxMessage.userId,
                subject = inboxMessage.subject?.let { "Re: $it" },
                text = response.text,
                conversationId = response.conversationId,
                role = Role.ASSISTANT,
            )
        )
    } catch (e: Exception) {
        LOGGER.warn("Failed to deliver response for ${inboxMessage.id}: ${e.message}")
    }
}
```

- [ ] **Step 5: Run tests**

```bash
mvn test -Dtest=InboxPollerTest
```

Expected: All tests pass

- [ ] **Step 6: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/service/inbox/InboxPoller.kt \
        src/test/kotlin/com/wutsi/kokibot/service/inbox/InboxPollerTest.kt
git commit -m "feat: forward usage and conversationId through InboxPoller"
```

---

### Task 3: Refactor `WebSocketChannel` to submit to Inbox

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannel.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannelTest.kt`

**Interfaces:**
- Consumes: `context.inbox: Inbox` (existing), `Message.usage: LLMUsage?` (Task 1), `message.conversationId` (existing on Message)
- Produces: sessions keyed by `session.id`; `handleMessage()` calls `context.inbox.submit()`; `sendStatus()` emits `REASONING_CHUNK` with `usage`; `send()` emits `FINAL` with `conversationId`

- [ ] **Step 1: Update test imports and setup to add Inbox mock**

At the top of `WebSocketChannelTest.kt`, add:
```kotlin
import com.wutsi.kokibot.service.inbox.Inbox
```

In the class body, add:
```kotlin
private val inbox = mock<Inbox>()
```

In `@BeforeEach setup()`, add:
```kotlin
whenever(context.inbox).doReturn(inbox)
```

And **remove** the `assistant.process()` default mock (the last two lines of the current `setup()`):
```kotlin
// DELETE these lines:
val msg = Message(text = "Final answer", role = Role.ASSISTANT, conversationId = "conv-test-123")
doReturn(msg).whenever(assistant).process(any(), any())
```

Also **remove** this import (no longer needed after refactor):
```kotlin
import com.wutsi.kokibot.channel.websocket.WebSocketChannel.Companion.ANONYMOUS_USER
```

- [ ] **Step 2: Write failing tests for the new behavior**

**Replace** `handleConnectionEstablished` test:

```kotlin
@Test
fun `handleConnectionEstablished stores session by session id`() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)

    channel.handleConnectionEstablished(session)

    assertEquals(session, channel.getSession("session-123"))
}
```

**Replace** `valid request returns final response` with inbox submission test:

```kotlin
@Test
fun `handleMessage submits message to inbox`() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    channel.handleMessage(
        session,
        """{"query": "Hello", "filePaths": ["/a.txt"], "conversationId": "conv-42"}""",
    )

    val captor = argumentCaptor<Message>()
    verify(inbox).submit(captor.capture())
    val submitted = captor.firstValue
    assertEquals("Hello", submitted.text)
    assertEquals("session-123", submitted.userId)
    assertEquals("channel:websocket", submitted.channelId)
    assertEquals(listOf("/a.txt"), submitted.filePaths)
    assertEquals("conv-42", submitted.conversationId)
    assertEquals(Role.USER, submitted.role)
}
```

**Replace** `backend issue sends error response` to mock inbox failure:

```kotlin
@Test
fun `handleMessage sends error when inbox fails`() {
    doThrow(RuntimeException("inbox full")).whenever(inbox).submit(any())

    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    channel.handleMessage(session, """{"query": "Hello", "filePaths": []}""")

    verify(session).sendMessage(
        argThat { msg ->
            val response = jsonMapper.readValue(
                (msg as TextMessage).payload,
                WebSocketResponse::class.java,
            )
            response.type == WebSocketResponseType.ERROR &&
                response.message?.contains("inbox full") == true
        },
    )
}
```

**Replace** `send` test to use `handleConnectionEstablished` and `session.id`:

```kotlin
@Test
fun send() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    val message = Message(
        text = "Hello",
        channelId = "channel:websocket",
        userId = "session-123",
        conversationId = "conv-xyz",
    )
    assertTrue(channel.send(message))

    verify(session).sendMessage(
        argThat { msg ->
            val response = jsonMapper.readValue(
                (msg as TextMessage).payload,
                WebSocketResponse::class.java,
            )
            response.type == WebSocketResponseType.FINAL &&
                response.content == "Hello" &&
                response.conversationId == "conv-xyz"
        },
    )
}
```

**Replace** `sendStatus sends tool status message` test to use `REASONING_CHUNK` and `usage`:

```kotlin
@Test
fun `sendStatus sends REASONING_CHUNK with usage`() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    val usage = LLMUsage(totalTokens = 50, promptTokens = 30, completionTokens = 20)
    val message = Message(
        text = "Thinking...",
        channelId = "channel:websocket",
        userId = "session-123",
        usage = usage,
        role = Role.ASSISTANT,
    )
    channel.sendStatus(message)

    verify(session).sendMessage(argThat { msg ->
        val response = jsonMapper.readValue(
            (msg as TextMessage).payload,
            WebSocketResponse::class.java,
        )
        response.type == WebSocketResponseType.REASONING_CHUNK &&
            response.content == "Thinking..." &&
            response.usage == usage
    })
}
```

**Replace** `handleConnectionClosed` test to use `handleConnectionEstablished`:

```kotlin
@Test
fun handleConnectionClosed() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    assertEquals(session, channel.getSession("session-123"))
    channel.handleConnectionClosed(session, CloseStatus.NORMAL)

    assertNull(channel.getSession("session-123"))
}
```

**Delete** `streaming sends multiple chunks` test entirely — inline streaming is replaced by InboxPoller.

**Update** `send returns false if session failed` to use `handleConnectionEstablished`:

```kotlin
@Test
fun `send returns false if session throws on send`() {
    val channel = WebSocketChannel()
    channel.init(emptyMap<String, Any>(), context)
    channel.handleConnectionEstablished(session)

    doThrow(RuntimeException("socket closed")).whenever(session).sendMessage(any())

    val message = Message(
        text = "Hello",
        channelId = "channel:websocket",
        userId = "session-123",
    )
    assertFalse(channel.send(message))
}
```

Add this import:
```kotlin
import com.wutsi.kokibot.llm.LLMUsage
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
mvn test -Dtest=WebSocketChannelTest
```

Expected: multiple FAILs — new tests don't match current behaviour

- [ ] **Step 4: Implement `handleConnectionEstablished` session storage**

In `WebSocketChannel.kt`, update `handleConnectionEstablished`:

```kotlin
internal fun handleConnectionEstablished(session: WebSocketSession) {
    LOGGER.info("WebSocket connection established: ${session.id}")
    sessions[session.id] = session
}
```

- [ ] **Step 5: Refactor `handleMessage` to submit to Inbox**

Replace the entire `handleMessage` method:

```kotlin
internal fun handleMessage(session: WebSocketSession, payload: String) {
    try {
        val request = jsonMapper.readValue(payload, WebSocketRequest::class.java)
        context.inbox.submit(
            Message(
                text = request.query,
                role = Role.USER,
                userId = session.id,
                channelId = id(),
                filePaths = request.filePaths,
                conversationId = request.conversationId,
            )
        )
    } catch (e: Exception) {
        LOGGER.error("Error submitting WebSocket message to inbox", e)
        try {
            sendError(session, e.message ?: "Internal error")
        } catch (ex: Exception) {
            LOGGER.error("Error sending error response to WebSocket", ex)
        }
    }
}
```

- [ ] **Step 6: Update `sendStatus` to emit `REASONING_CHUNK` with usage**

Replace the `sendStatus` method:

```kotlin
override fun sendStatus(message: Message) {
    if (message.channelId != id()) {
        return
    }

    val session = sessions[message.userId] ?: return

    try {
        val response = WebSocketResponse(
            type = WebSocketResponseType.REASONING_CHUNK,
            content = message.text,
            usage = message.usage,
        )
        session.sendMessage(TextMessage(jsonMapper.writeValueAsString(response)))
    } catch (e: Exception) {
        LOGGER.warn("Error sending status to WebSocket: ${e.message}")
    }
}
```

- [ ] **Step 7: Update `send` to include `conversationId`**

Replace the `send` method:

```kotlin
override fun send(message: Message): Boolean {
    if (message.channelId != id()) {
        return false
    }

    val session = sessions[message.userId] ?: return false

    try {
        val response = WebSocketResponse(
            type = WebSocketResponseType.FINAL,
            content = message.text,
            conversationId = message.conversationId,
        )
        session.sendMessage(TextMessage(jsonMapper.writeValueAsString(response)))
        return true
    } catch (e: Exception) {
        LOGGER.error("Error sending message to WebSocket", e)
        return false
    }
}
```

- [ ] **Step 8: Remove dead private methods**

Delete `sendReasoningChunk` and `sendFinalResponse` — they are no longer called after removing inline processing. Keep `sendError` and the private `sendMessage` helper.

- [ ] **Step 9: Run the WebSocketChannel tests**

```bash
mvn test -Dtest=WebSocketChannelTest
```

Expected: All tests pass

- [ ] **Step 10: Run the full test suite**

```bash
mvn test
```

Expected: All tests pass, coverage ≥ 90%

- [ ] **Step 11: Format and commit**

```bash
mvn antrun:run@ktlint-format
git add src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannel.kt \
        src/test/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannelTest.kt
git commit -m "feat: integrate WebSocketChannel with Inbox for queued, per-session processing"
```
