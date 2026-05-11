package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.assertFalse
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
        chatHistory.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun id() {
        assertEquals(ChatHistory.ID, chatHistory.id())
    }

    @Test
    fun append() {
        chatHistory.append(query, response)

        val expectedContent = """
            # ${query.dateTime}: Session ${query.id}
            ## ${query.role}
            ### Query:
            ```markdown
            ${query.text}
            ```
            ### Files:
            - file1.txt
            - file2.txt

            ## ${response.role}
            ### Response:
            ```markdown
            ${response.text}
            ```

            ---


        """.trimIndent()

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertTrue(file.exists())
        assertEquals(expectedContent, file.readText())
    }

    @Test
    fun `no user-id`() {
        chatHistory.append(query.copy(userId = null), response)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertFalse(file.exists())
    }

    @Test
    fun `no channel-id`() {
        chatHistory.append(query.copy(channelId = null), response)

        val today = query.dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val file = File(context.home.absolutePath + "/memory/chat/user-1/telegram/$today.md")
        assertFalse(file.exists())
    }
}
