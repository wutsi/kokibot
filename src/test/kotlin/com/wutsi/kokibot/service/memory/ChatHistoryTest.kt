package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.service.memory.ConversationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatHistoryTest {
    private val context = Context(
        home = File("target/test-data/chat-history"),
        llm = mock()
    )
    private val chatHistory = ChatHistory()
    private val query = Message(
        id = UUID.randomUUID().toString(),
        userId = "user-1",
        channelId = "channel:telegram",
        role = Role.USER,
        text = "Hello, world!",
        filePaths = listOf("file1.txt", "file2.txt")
    )
    private val response = Message(
        id = "response-1",
        userId = null,
        channelId = null,
        role = Role.ASSISTANT,
        text = "Hi there!",
        filePaths = emptyList()
    )

    @BeforeEach
    fun setup() {
        context.home.deleteRecursively()
        context.conversationRepository.init(emptyMap<String, Any>(), context)
        chatHistory.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun id() {
        assertEquals(ChatHistory.ID, chatHistory.id())
    }

    @Test
    fun append() {
        val conversationId = chatHistory.append(query, response)

        assertTrue(conversationId.isNotBlank())

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
                "```" +
                ConversationRepository.BLOCK_SEPARATOR

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertTrue(file.exists())
        assertEquals(expectedContent, file.readText())
    }

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
        val indexFile = File(context.home.absolutePath + "/memory/chat/user-1/conversations.json")
        assertFalse(indexFile.exists())
    }

    @Test
    fun `append returns empty string when userId is null`() {
        val result = chatHistory.append(query.copy(userId = null), response)
        assertEquals("", result)
    }

    @Test
    fun `append - no user-id`() {
        chatHistory.append(query.copy(userId = null), response)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertFalse(file.exists())
    }

    @Test
    fun `append - no channel-id`() {
        chatHistory.append(query.copy(channelId = null), response)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertFalse(file.exists())
    }

    @Test
    fun get() {
        chatHistory.append(query, response)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertEquals(file.readText(), chatHistory.get(query.userId!!, query.channelId!!))
    }

    @Test
    fun `get - not found`() {
        assertNull(chatHistory.get(query.userId!!, query.channelId!!))
    }

    @Test
    fun clear() {
        chatHistory.append(query, response)
        chatHistory.clear(query.userId, query.channelId)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertFalse(file.exists())
    }

    @Test
    fun `clear - no channeId`() {
        chatHistory.append(query, response)
        chatHistory.clear(query.userId, null)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertTrue(file.exists())
    }

    @Test
    fun `clear - no userId`() {
        chatHistory.append(query, response)
        chatHistory.clear(null, "telegram")

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertTrue(file.exists())
    }
}
