# Chat History Conversations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track chat messages grouped into named conversations, expose them via REST API, and propagate the active `conversationId` through the WebSocket layer so the UI can manage conversation state.

**Architecture:** A new `ConversationRepository` service manages a per-user JSON index (`memory/chat/{userId}/conversations.json`) and parses existing per-day markdown files to reconstruct message history. `ChatHistory.append()` creates a new conversation when `query.conversationId` is null and stamps each markdown block with a `<!-- kokibot:conv:{id} -->` marker. The conversation ID flows back to the UI via `WebSocketResponse`, and the UI re-sends it on subsequent messages.

**Tech Stack:** Kotlin, Spring Boot, Jackson, JUnit 5, Mockito Kotlin — no new dependencies required.

---

## File Map

### New files
| File | Responsibility |
|---|---|
| `src/main/kotlin/com/wutsi/kokibot/service/memory/Conversation.kt` | Data models: `Conversation`, `ConversationMessage`, `ConversationDetail` |
| `src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt` | CRUD for conversations.json + markdown parsing |
| `src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt` | REST endpoints: list and get conversation |
| `src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt` | Unit tests for ConversationRepository |
| `src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt` | Integration tests for ConversationController |

### Modified files
| File | Change |
|---|---|
| `src/main/kotlin/com/wutsi/kokibot/Message.kt` | Add `conversationId: String? = null` |
| `src/main/kotlin/com/wutsi/kokibot/Context.kt` | Add `conversationRepository` field; init in `initMemory()` |
| `src/main/kotlin/com/wutsi/kokibot/service/memory/ChatHistory.kt` | `append()` returns `String` (conversationId); write marker; create conversation when needed |
| `src/main/kotlin/com/wutsi/kokibot/Assistant.kt` | Store result of `chatHistory.append()`; return `response.copy(conversationId = …)` |
| `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketRequest.kt` | Add `conversationId: String? = null` |
| `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketResponse.kt` | Add `conversationId: String? = null` |
| `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannel.kt` | Pass `conversationId` from request into `Message`; return it in `sendFinalResponse` |
| `src/test/kotlin/com/wutsi/kokibot/service/memory/ChatHistoryTest.kt` | Update for new return type, new markdown format, and marker presence |
| `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt` | Update mock stub for `chatHistory.append()` to return `String` |
| `src/test/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannelTest.kt` | Update assertions to include `conversationId` in response |

---

## Task 1: Data models

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/service/memory/Conversation.kt`

- [ ] **Step 1: Create the data models**

```kotlin
package com.wutsi.kokibot.service.memory

import java.time.LocalDateTime

data class Conversation(
    val id: String,
    val channelId: String,
    val title: String,
    val startDate: LocalDateTime,
)

data class ConversationMessage(
    val role: String,
    val text: String,
    val dateTime: LocalDateTime,
)

data class ConversationDetail(
    val id: String,
    val title: String,
    val startDate: LocalDateTime,
    val messages: List<ConversationMessage>,
)
```

- [ ] **Step 2: Build**

```bash
mvn antrun:run@ktlint-format && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/service/memory/Conversation.kt
git commit -m "feat: add Conversation, ConversationMessage, ConversationDetail data models"
```

---

## Task 2: Add `conversationId` to `Message`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Message.kt`

- [ ] **Step 1: Add the field**

In `Message.kt`, add `conversationId: String? = null` as the last field:

```kotlin
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
)
```

- [ ] **Step 2: Build and run tests**

```bash
mvn antrun:run@ktlint-format && mvn test -q
```
Expected: BUILD SUCCESS — no tests should break since the field has a default value.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Message.kt
git commit -m "feat: add conversationId field to Message"
```

---

## Task 3: Implement `ConversationRepository`

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    private val home = File("target/test-data/conversation-repository")
    private val context = Context(home = home, llm = mock())
    private val repo = ConversationRepository()

    @BeforeEach
    fun setup() {
        home.deleteRecursively()
        repo.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun id() {
        assertEquals(ConversationRepository.ID, repo.id())
    }

    @Test
    fun `createConversation writes entry to index file`() {
        val conv = repo.createConversation("user-1", "telegram", "Hello world")

        assertNotNull(conv.id)
        assertEquals("telegram", conv.channelId)
        assertEquals("Hello world", conv.title)
        assertNotNull(conv.startDate)

        val indexFile = File(home, "memory/chat/user-1/conversations.json")
        assertTrue(indexFile.exists())
    }

    @Test
    fun `createConversation truncates long title`() {
        val longText = "A".repeat(100)
        val conv = repo.createConversation("user-1", "telegram", longText)

        assertEquals(ConversationRepository.TITLE_MAX_LENGTH, conv.title.length)
    }

    @Test
    fun `createConversation sanitizes channelId`() {
        val conv = repo.createConversation("user-1", "channel:telegram", "Hello")
        assertEquals("telegram", conv.channelId)
    }

    @Test
    fun `getConversations returns empty list when no index file`() {
        val result = repo.getConversations("user-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getConversations returns all conversations for user`() {
        repo.createConversation("user-1", "telegram", "First")
        repo.createConversation("user-1", "telegram", "Second")

        val result = repo.getConversations("user-1")
        assertEquals(2, result.size)
    }

    @Test
    fun `getConversations filters by channelId`() {
        repo.createConversation("user-1", "telegram", "Telegram convo")
        repo.createConversation("user-1", "channel:websocket", "WebSocket convo")

        val result = repo.getConversations("user-1", "telegram")
        assertEquals(1, result.size)
        assertEquals("telegram", result[0].channelId)
    }

    @Test
    fun `getConversations returns conversations sorted by startDate descending`() {
        val c1 = repo.createConversation("user-1", "telegram", "First")
        Thread.sleep(10)
        val c2 = repo.createConversation("user-1", "telegram", "Second")

        val result = repo.getConversations("user-1")
        assertEquals(c2.id, result[0].id)
        assertEquals(c1.id, result[1].id)
    }

    @Test
    fun `getMessages returns empty list when conversation not found`() {
        val result = repo.getMessages("unknown-id", "user-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMessages parses messages from markdown file`() {
        val conv = repo.createConversation("user-1", "telegram", "Weather query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# 2026-06-12T10:00:00: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "What is the weather?\n" +
                "```\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Sunny, 22°C.\n" +
                "```\n" +
                "\n" +
                "---\n\n"
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("What is the weather?", messages[0].text)
        assertEquals("assistant", messages[1].role)
        assertEquals("Sunny, 22°C.", messages[1].text)
    }

    @Test
    fun `getMessages ignores blocks from other conversations`() {
        val conv1 = repo.createConversation("user-1", "telegram", "First")
        val conv2 = repo.createConversation("user-1", "telegram", "Second")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv1.id} -->\n" +
                "# 2026-06-12T10:00:00: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "First question\n" +
                "```\n\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "First answer\n" +
                "```\n\n" +
                "---\n\n" +
                "<!-- kokibot:conv:${conv2.id} -->\n" +
                "# 2026-06-12T11:00:00: Session def\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Second question\n" +
                "```\n\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Second answer\n" +
                "```\n\n" +
                "---\n\n"
        )

        val messages = repo.getMessages(conv1.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("First question", messages[0].text)
        assertEquals("First answer", messages[1].text)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ConversationRepositoryTest -q 2>&1 | tail -5
```
Expected: FAIL with "cannot find symbol" or similar compilation error.

- [ ] **Step 3: Implement `ConversationRepository`**

```kotlin
package com.wutsi.kokibot.service.memory

import com.fasterxml.jackson.core.type.TypeReference
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ConversationRepository : Resource {
    companion object {
        const val ID = "service:conversation-repository"
        const val TITLE_MAX_LENGTH = 60
        private const val CONV_MARKER_PREFIX = "<!-- kokibot:conv:"
        private const val CONV_MARKER_SUFFIX = " -->"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private lateinit var context: Context
    private val lock = ReentrantReadWriteLock()

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun createConversation(userId: String, channelId: String, firstMessage: String): Conversation {
        lock.write {
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                channelId = sanitizeId(channelId),
                title = firstMessage.take(TITLE_MAX_LENGTH),
                startDate = LocalDateTime.now(),
            )
            val conversations = readIndex(userId).toMutableList()
            conversations.add(conversation)
            writeIndex(userId, conversations)
            return conversation
        }
    }

    fun getConversations(userId: String, channelId: String? = null): List<Conversation> {
        lock.read {
            val sanitized = channelId?.let { sanitizeId(it) }
            return readIndex(userId)
                .filter { sanitized == null || it.channelId == sanitized }
                .sortedByDescending { it.startDate }
        }
    }

    fun getMessages(conversationId: String, userId: String): List<ConversationMessage> {
        lock.read {
            val conversation = readIndex(userId).find { it.id == conversationId }
                ?: return emptyList()

            val startDate = conversation.startDate.toLocalDate()
            val channelDir = File(
                "${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}/${conversation.channelId}"
            )
            if (!channelDir.exists()) return emptyList()

            return channelDir.listFiles { f -> f.extension == "md" }
                ?.filter { f ->
                    runCatching { LocalDate.parse(f.nameWithoutExtension, DATE_FORMAT) >= startDate }.getOrDefault(false)
                }
                ?.sortedBy { f -> f.nameWithoutExtension }
                ?.flatMap { f -> parseMessages(f, conversationId) }
                ?: emptyList()
        }
    }

    private fun parseMessages(file: File, conversationId: String): List<ConversationMessage> {
        val content = file.readText()
        val blocks = content.split("\n\n---\n\n")
        val messages = mutableListOf<ConversationMessage>()

        for (block in blocks) {
            val trimmed = block.trim()
            val marker = "$CONV_MARKER_PREFIX$conversationId$CONV_MARKER_SUFFIX"
            if (!trimmed.startsWith(marker)) continue

            val dateTime = extractDateTime(trimmed)
            val userText = extractSection(trimmed, "### Query:")
            val assistantText = extractSection(trimmed, "### Response:")

            if (userText != null) {
                messages.add(ConversationMessage(role = "user", text = userText, dateTime = dateTime))
            }
            if (assistantText != null) {
                messages.add(ConversationMessage(role = "assistant", text = assistantText, dateTime = dateTime))
            }
        }
        return messages
    }

    private fun extractSection(block: String, header: String): String? {
        val marker = "$header\n```markdown\n"
        val start = block.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = block.indexOf("\n```", contentStart)
        if (end == -1) return null
        return block.substring(contentStart, end)
    }

    private fun extractDateTime(block: String): LocalDateTime {
        val line = block.lines().firstOrNull { it.startsWith("# ") } ?: return LocalDateTime.now()
        return runCatching {
            LocalDateTime.parse(line.removePrefix("# ").substringBefore(": Session "))
        }.getOrDefault(LocalDateTime.now())
    }

    private fun getIndexFile(userId: String): File {
        val dir = File("${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "conversations.json")
    }

    private fun readIndex(userId: String): List<Conversation> {
        val file = getIndexFile(userId)
        if (!file.exists()) return emptyList()
        return context.jsonMapper.readValue(file.readText(), object : TypeReference<List<Conversation>>() {})
    }

    private fun writeIndex(userId: String, conversations: List<Conversation>) {
        val file = getIndexFile(userId)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(context.jsonMapper.writeValueAsString(conversations))
        tmp.renameTo(file)
    }

    private fun sanitizeId(id: String): String =
        id.removePrefix("channel:").replace(":", "-")
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=ConversationRepositoryTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/service/memory/ConversationRepository.kt \
        src/test/kotlin/com/wutsi/kokibot/service/memory/ConversationRepositoryTest.kt
git commit -m "feat: implement ConversationRepository with index and markdown parsing"
```

---

## Task 4: Wire `ConversationRepository` into `Context`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Context.kt`

- [ ] **Step 1: Add the field and initialization**

Add the import and field to `Context`:

```kotlin
import com.wutsi.kokibot.service.memory.ConversationRepository
```

Add the field in the constructor (after `chatHistory`):

```kotlin
val conversationRepository: ConversationRepository = ConversationRepository(),
```

In `resources()`, add `conversationRepository` to the list:

```kotlin
listOf(llm, memory, dailyLog, sessionLog, chatHistory, conversationRepository, fileService, heartbeat, delegationStack)
```

In `initMemory()`, initialize `conversationRepository` **before** `chatHistory`:

```kotlin
private fun initMemory(config: Map<*, *>) {
    val root = MapUtil.toMap("memory", config) ?: emptyMap<String, Any>()

    memory.init(root, this)
    dailyLog.init(root, this)
    sessionLog.init(root, this)
    conversationRepository.init(root, this)
    chatHistory.init(root, this)
}
```

- [ ] **Step 2: Build and run full test suite**

```bash
mvn antrun:run@ktlint-format && mvn test -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Context.kt
git commit -m "feat: add ConversationRepository to Context"
```

---

## Task 5: Update `ChatHistory` to stamp markers and return `conversationId`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/service/memory/ChatHistory.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/service/memory/ChatHistoryTest.kt`

- [ ] **Step 1: Update the failing tests first**

In `ChatHistoryTest.kt`, update the `append` test to reflect the new markdown format and the `String` return type. Also add tests for the conversation creation and reuse.

Replace the existing `append` test body with:

```kotlin
@Test
fun append() {
    val conversationId = chatHistory.append(query, response)

    val expectedContent =
        "<!-- kokibot:conv:$conversationId -->\n" +
            "# ${query.dateTime}: Session ${query.id}\n" +
            "## ${query.role}\n" +
            "### Query:\n" +
            "```markdown\n" +
            "${query.text}\n" +
            "```\n" +
            "### Files:\n" +
            "- file1.txt\n" +
            "- file2.txt\n\n" +
            "## ${response.role}\n" +
            "### Response:\n" +
            "```markdown\n" +
            "${response.text}\n" +
            "```\n\n" +
            "---\n\n"

    val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
    assertTrue(file.exists())
    assertEquals(expectedContent, file.readText())
}
```

Add these new tests:

```kotlin
@Test
fun `append creates new conversation when conversationId is null`() {
    val conversationId = chatHistory.append(query, response)

    assertTrue(conversationId.isNotBlank())
    val indexFile = File(context.home.absolutePath + "/memory/chat/user-1/conversations.json")
    assertTrue(indexFile.exists())
}

@Test
fun `append reuses conversationId when provided`() {
    val existingId = "existing-conv-123"
    val queryWithConv = query.copy(conversationId = existingId)

    val returned = chatHistory.append(queryWithConv, response)

    assertEquals(existingId, returned)
    // Index should NOT contain a new entry for a pre-existing ID
    val indexFile = File(context.home.absolutePath + "/memory/chat/user-1/conversations.json")
    assertFalse(indexFile.exists())
}

@Test
fun `append returns empty string when userId is null`() {
    val result = chatHistory.append(query.copy(userId = null), response)
    assertEquals("", result)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ChatHistoryTest -q 2>&1 | tail -10
```
Expected: compilation errors or assertion failures.

- [ ] **Step 3: Update `ChatHistory.append()` implementation**

Replace the entire `append` function:

```kotlin
fun append(query: Message, response: Message): String {
    lock.write {
        val userId = query.userId ?: return ""
        val channelId = query.channelId ?: return ""

        val conversationId = query.conversationId
            ?: context.conversationRepository.createConversation(userId, channelId, query.text).id

        val files = query.filePaths.joinToString("\n") { file -> "- $file" }

        val content = "<!-- kokibot:conv:$conversationId -->\n" +
            "# ${query.dateTime}: Session ${query.id}\n" +
            "## ${query.role}\n" +
            "### Query:\n" +
            "```markdown\n${query.text}\n```\n" +
            (if (files.isNotEmpty()) "### Files:\n$files\n\n" else "\n") +
            "## ${response.role}\n" +
            "### Response:\n" +
            "```markdown\n${response.text}\n```\n\n" +
            "---\n\n"

        getFile(userId, channelId).appendText(content)
        return conversationId
    }
}
```

Also add `private lateinit var context: Context` field and update `init`:

```kotlin
override fun init(config: Map<*, *>, context: Context) {
    this.context = context
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=ChatHistoryTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Run full test suite to catch regressions**

```bash
mvn test -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/service/memory/ChatHistory.kt \
        src/test/kotlin/com/wutsi/kokibot/service/memory/ChatHistoryTest.kt
git commit -m "feat: ChatHistory.append() stamps conv marker and returns conversationId"
```

---

## Task 6: Propagate `conversationId` through `Assistant.process()`

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

- [ ] **Step 1: Update the mock stub in `AssistantTest`**

In `AssistantTest.kt`, the `chatHistory` mock currently has `append()` returning `Unit`. Update any stub or verify calls to match the new `String` return type. Find lines like:

```kotlin
private val chatHistory = mock<ChatHistory>()
```

Add a default stub in `@BeforeEach`:

```kotlin
doReturn("conv-test-123").whenever(chatHistory).append(any(), any())
```

- [ ] **Step 2: Run tests to verify they fail before the fix**

```bash
mvn test -Dtest=AssistantTest -q 2>&1 | tail -10
```
Expected: compilation error about type mismatch on `append()` return.

- [ ] **Step 3: Update `Assistant.process()` to store and return `conversationId`**

In `Assistant.kt`, replace:

```kotlin
context.chatHistory.append(query, response)
context.sessionLog.onResponse(query.id, response)
return response
```

with:

```kotlin
val conversationId = context.chatHistory.append(query, response)
context.sessionLog.onResponse(query.id, response)
return response.copy(conversationId = conversationId)
```

- [ ] **Step 4: Run the full test suite**

```bash
mvn antrun:run@ktlint-format && mvn test -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt \
        src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "feat: Assistant.process() returns response with conversationId"
```

---

## Task 7: Propagate `conversationId` through the WebSocket layer

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketRequest.kt`
- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketResponse.kt`
- Modify: `src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannel.kt`
- Modify: `src/test/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannelTest.kt`

- [ ] **Step 1: Update `WebSocketRequest`**

```kotlin
package com.wutsi.kokibot.channel.websocket

data class WebSocketRequest(
    val query: String,
    val filePaths: List<String> = emptyList(),
    val conversationId: String? = null,
)
```

- [ ] **Step 2: Update `WebSocketResponse`**

```kotlin
package com.wutsi.kokibot.channel.websocket

import com.wutsi.kokibot.llm.LLMUsage

data class WebSocketResponse(
    val type: WebSocketResponseType,
    val content: String? = null,
    val message: String? = null,
    val finishReason: String? = null,
    val usage: LLMUsage? = null,
    val conversationId: String? = null,
)
```

- [ ] **Step 3: Update `WebSocketChannel.handleMessage()` and `sendFinalResponse()`**

In `handleMessage()`, pass `conversationId` from the request into the `Message`:

```kotlin
val message = Message(
    text = decorateQuery(request.query),
    role = Role.USER,
    userId = userId,
    channelId = id(),
    filePaths = request.filePaths,
    conversationId = request.conversationId,
)
```

Replace the final response dispatch at the end of `handleMessage()`:

```kotlin
sendFinalResponse(session, response.text, response.conversationId, lastUsage)
```

Update the `sendFinalResponse` private method signature and body:

```kotlin
private fun sendFinalResponse(session: WebSocketSession, content: String, conversationId: String?, usage: LLMUsage?) {
    sendMessage(
        session,
        WebSocketResponse(
            type = WebSocketResponseType.FINAL,
            content = content,
            finishReason = "DONE",
            usage = usage,
            conversationId = conversationId,
        ),
    )
}
```

- [ ] **Step 4: Update `WebSocketChannelTest` to assert `conversationId` is returned**

In `WebSocketChannelTest`, update the setup stub so `assistant.process()` returns a message with a `conversationId`:

```kotlin
val msg = Message(text = "Final answer", role = Role.ASSISTANT, conversationId = "conv-test-123")
doReturn(msg).whenever(assistant).process(any(), any())
```

Update the `valid request returns final response` test assertion to also check `conversationId`:

```kotlin
response.type == WebSocketResponseType.FINAL &&
    response.content == "Hello, world!" &&
    response.conversationId == "conv-test-123"
```

- [ ] **Step 5: Run all WebSocket tests**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=WebSocketChannelTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 6: Run full test suite**

```bash
mvn test -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketRequest.kt \
        src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketResponse.kt \
        src/main/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannel.kt \
        src/test/kotlin/com/wutsi/kokibot/channel/websocket/WebSocketChannelTest.kt
git commit -m "feat: propagate conversationId through WebSocket request/response"
```

---

## Task 8: Add `ConversationController` REST API

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt`

- [ ] **Step 1: Write the failing integration tests**

```kotlin
package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.memory.Conversation
import com.wutsi.kokibot.service.memory.ConversationDetail
import com.wutsi.kokibot.service.memory.ConversationMessage
import com.wutsi.kokibot.service.memory.ConversationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ConversationControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    private val conversationRepository = mock<ConversationRepository>()

    @BeforeEach
    fun setup() {
        val assistant = mock<Assistant>()
        doReturn("my-agent").whenever(assistant).name

        val context = mock<Context>()
        doReturn(assistant).whenever(context).assistant
        doReturn(conversationRepository).whenever(context).conversationRepository

        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()

        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
    }

    @Test
    fun `list returns conversations for user`() {
        val conversations = listOf(
            Conversation(
                id = "conv-001",
                channelId = "telegram",
                title = "Weather in Paris",
                startDate = LocalDateTime.of(2026, 6, 12, 10, 0),
            ),
        )
        doReturn(conversations).whenever(conversationRepository).getConversations("user-1", null)

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations?userId=user-1",
            List::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body!!.size)
    }

    @Test
    fun `list returns 404 when assistant not found`() {
        val response = rest.getForEntity(
            "/assistants/unknown/conversations?userId=user-1",
            Any::class.java,
        )
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get returns conversation detail`() {
        val conversation = Conversation(
            id = "conv-001",
            channelId = "telegram",
            title = "Weather in Paris",
            startDate = LocalDateTime.of(2026, 6, 12, 10, 0),
        )
        val messages = listOf(
            ConversationMessage("user", "What's the weather?", LocalDateTime.of(2026, 6, 12, 10, 0)),
            ConversationMessage("assistant", "Sunny, 22°C.", LocalDateTime.of(2026, 6, 12, 10, 0)),
        )
        doReturn(listOf(conversation)).whenever(conversationRepository).getConversations("user-1", null)
        doReturn(messages).whenever(conversationRepository).getMessages("conv-001", "user-1")

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations/conv-001?userId=user-1",
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("conv-001", response.body!!["id"])
        assertEquals("Weather in Paris", response.body!!["title"])
    }

    @Test
    fun `get returns 404 when conversation not found`() {
        doReturn(emptyList<Conversation>()).whenever(conversationRepository).getConversations("user-1", null)

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations/unknown?userId=user-1",
            Any::class.java,
        )
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get returns 404 when assistant not found`() {
        val response = rest.getForEntity(
            "/assistants/unknown/conversations/conv-001?userId=user-1",
            Any::class.java,
        )
        assertEquals(404, response.statusCode.value())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=ConversationControllerTest -q 2>&1 | tail -10
```
Expected: 404 for all endpoints since the controller doesn't exist yet.

- [ ] **Step 3: Implement `ConversationController`**

```kotlin
package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.memory.ConversationDetail
import com.wutsi.kokibot.service.memory.ConversationRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assistants")
class ConversationController(private val multi: MultiBootstrap) {

    @GetMapping("/{name}/conversations")
    fun list(
        @PathVariable name: String,
        @RequestParam userId: String,
        @RequestParam(required = false) channelId: String?,
    ): ResponseEntity<Any> {
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(repository.getConversations(userId, channelId))
    }

    @GetMapping("/{name}/conversations/{id}")
    fun get(
        @PathVariable name: String,
        @PathVariable id: String,
        @RequestParam userId: String,
    ): ResponseEntity<Any> {
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        val conversation = repository.getConversations(userId).find { it.id == id }
            ?: return ResponseEntity.notFound().build()
        val messages = repository.getMessages(id, userId)
        return ResponseEntity.ok(
            ConversationDetail(
                id = conversation.id,
                title = conversation.title,
                startDate = conversation.startDate,
                messages = messages,
            )
        )
    }

    private fun getRepository(name: String): ConversationRepository? =
        multi.bootstraps
            .firstOrNull { it.getContext().assistant.name == name }
            ?.getContext()
            ?.conversationRepository
}
```

- [ ] **Step 4: Run the controller tests**

```bash
mvn antrun:run@ktlint-format && mvn test -Dtest=ConversationControllerTest -q
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 5: Run full test suite**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, coverage >= 90%.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/kokibot/controller/ConversationController.kt \
        src/test/kotlin/com/wutsi/kokibot/controller/ConversationControllerTest.kt
git commit -m "feat: add ConversationController REST API"
```

---

## Final verification

- [ ] **Run full build including coverage check**

```bash
mvn clean install -q
```
Expected: BUILD SUCCESS, jacoco coverage thresholds pass (90% line, 90% class).

- [ ] **Open coverage report and verify new classes are covered**

```bash
open target/site/jacoco/index.html
```
Check: `ConversationRepository`, `ConversationController` both show ≥ 90% line coverage.
