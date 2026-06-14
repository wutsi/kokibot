package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
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
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
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
                "```" + ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("What is the weather?", messages[0].text)
        assertEquals("assistant", messages[1].role)
        assertEquals("Sunny, 22°C.", messages[1].text)
    }

    @Test
    fun `getConversations respects limit`() {
        repeat(5) { i -> repo.createConversation("user-1", "telegram", "Conv $i") }

        val result = repo.getConversations("user-1", limit = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `getConversations respects offset`() {
        repeat(5) { i ->
            Thread.sleep(5)
            repo.createConversation("user-1", "telegram", "Conv $i")
        }
        val all = repo.getConversations("user-1")
        val paginated = repo.getConversations("user-1", offset = 2)

        assertEquals(all.size - 2, paginated.size)
        assertEquals(all[2].id, paginated[0].id)
    }

    @Test
    fun `getMessages ignores blocks from other conversations`() {
        val conv1 = repo.createConversation("user-1", "telegram", "First")
        val conv2 = repo.createConversation("user-1", "telegram", "Second")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val later = now.plusHours(1)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv1.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "First question\n" +
                "```\n\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "First answer\n" +
                "```" + ConversationRepository.BLOCK_SEPARATOR +
                "<!-- kokibot:conv:${conv2.id} -->\n" +
                "# $later: Session def\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Second question\n" +
                "```\n\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Second answer\n" +
                "```" + ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv1.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("First question", messages[0].text)
        assertEquals("First answer", messages[1].text)
    }

    @Test
    fun `getMessages parses file attachments from markdown`() {
        val conv = repo.createConversation("user-1", "telegram", "Upload query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Summarize this\n" +
                "```\n" +
                "### Files:\n" +
                "- /workspace/files/report.pdf\n" +
                "- /workspace/files/data.csv\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Here is the summary.\n" +
                "```" + ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        val userMsg = messages[0]
        assertEquals("user", userMsg.role)
        assertEquals("Summarize this", userMsg.text)
        assertEquals(listOf("/workspace/files/report.pdf", "/workspace/files/data.csv"), userMsg.files)
        assertEquals(emptyList<String>(), messages[1].files)
    }

    @Test
    fun `getMessages returns empty files list when no attachments`() {
        val conv = repo.createConversation("user-1", "telegram", "Plain query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Hello\n" +
                "```\n\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Hi there.\n" +
                "```" + ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals(emptyList<String>(), messages[0].files)
        assertEquals(emptyList<String>(), messages[1].files)
    }

    @Test
    fun `getMessages preserves code blocks in response`() {
        val conv = repo.createConversation("user-1", "telegram", "Code query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Fix this code\n" +
                "```\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Here is the fix:\n" +
                "```python\n" +
                "x = 2\n" +
                "```\n" +
                "Done.\n" +
                "```" +
                ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("Here is the fix:\n```python\nx = 2\n```\nDone.", messages[1].text)
    }

    @Test
    fun `getMessages preserves code blocks in query`() {
        val conv = repo.createConversation("user-1", "telegram", "Code query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Fix this:\n" +
                "```python\n" +
                "x = 1\n" +
                "```\n" +
                "Please.\n" +
                "```\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Done.\n" +
                "```" +
                ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("Fix this:\n```python\nx = 1\n```\nPlease.", messages[0].text)
    }

    @Test
    fun `round-trip via ChatHistory append and getMessages`() {
        val chatHistory = ChatHistory()
        context.conversationRepository.init(emptyMap<String, Any>(), context)
        chatHistory.init(emptyMap<String, Any>(), context)

        val query = Message(
            text = "What is the weather?",
            role = Role.USER,
            userId = "anonymous",
            channelId = "channel:websocket",
        )
        val response = Message(
            text = "Sunny, 22°C.",
            role = Role.ASSISTANT,
        )
        val convId = chatHistory.append(query, response)

        val messages = context.conversationRepository.getMessages(convId, "anonymous")

        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("What is the weather?", messages[0].text)
        assertEquals("assistant", messages[1].role)
        assertEquals("Sunny, 22°C.", messages[1].text)
    }

    @Test
    fun `getMessages parses response containing markdown headings`() {
        val conv = repo.createConversation("user-1", "telegram", "Heading query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Explain something\n" +
                "```\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "## Overview\n" +
                "Here is the overview.\n" +
                "\n" +
                "## Details\n" +
                "More details here.\n" +
                "```" +
                ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("## Overview\nHere is the overview.\n\n## Details\nMore details here.", messages[1].text)
    }

    @Test
    fun `round-trip with code block in response`() {
        val chatHistory = ChatHistory()
        context.conversationRepository.init(emptyMap<String, Any>(), context)
        chatHistory.init(emptyMap<String, Any>(), context)

        val query = Message(
            text = "Show me Python code",
            role = Role.USER,
            userId = "anonymous",
            channelId = "channel:websocket",
        )
        val response = Message(
            text = "Here is the code:\n```python\nprint('hello')\n```\nDone.",
            role = Role.ASSISTANT,
        )
        val convId = chatHistory.append(query, response)

        val messages = context.conversationRepository.getMessages(convId, "anonymous")

        assertEquals(2, messages.size)
        assertEquals("Here is the code:\n```python\nprint('hello')\n```\nDone.", messages[1].text)
    }

    @Test
    fun `getMessages handles horizontal rule in response without splitting`() {
        val conv = repo.createConversation("user-1", "telegram", "HR query")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val now = LocalDateTime.now().withNano(0)
        val mdFile = File(home, "memory/chat/user-1/telegram/$today.md")
        mdFile.parentFile.mkdirs()
        mdFile.writeText(
            "<!-- kokibot:conv:${conv.id} -->\n" +
                "# $now: Session abc\n" +
                "## user\n" +
                "### Query:\n" +
                "```markdown\n" +
                "Summarize\n" +
                "```\n" +
                "\n" +
                "## assistant\n" +
                "### Response:\n" +
                "```markdown\n" +
                "Part one.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Part two.\n" +
                "```" +
                ConversationRepository.BLOCK_SEPARATOR
        )

        val messages = repo.getMessages(conv.id, "user-1")

        assertEquals(2, messages.size)
        assertEquals("Part one.\n\n---\n\nPart two.", messages[1].text)
    }
}
