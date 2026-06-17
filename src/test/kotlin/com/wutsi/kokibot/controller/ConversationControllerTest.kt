package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.channel.websocket.WebSocketChannel
import com.wutsi.kokibot.service.memory.Conversation
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
        doReturn(conversations)
            .whenever(conversationRepository)
            .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations",
            List::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body!!.size)
    }

    @Test
    fun `list passes limit and offset to repository`() {
        doReturn(emptyList<Conversation>())
            .whenever(conversationRepository)
            .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), eq(5), eq(10))

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations?limit=5&offset=10",
            List::class.java,
        )

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `list returns 404 when assistant not found`() {
        val response = rest.getForEntity(
            "/assistants/unknown/conversations",
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
            ConversationMessage("user", "What's the weather?", dateTime = LocalDateTime.of(2026, 6, 12, 10, 0)),
            ConversationMessage("assistant", "Sunny, 22°C.", dateTime = LocalDateTime.of(2026, 6, 12, 10, 0)),
        )
        doReturn(listOf(conversation)).whenever(conversationRepository)
            .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())

        doReturn(messages).whenever(conversationRepository).getMessages("conv-001", WebSocketChannel.ANONYMOUS_USER, "telegram")

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations/conv-001",
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("conv-001", response.body!!["id"])
        assertEquals("Weather in Paris", response.body!!["title"])
    }

    @Test
    fun `get returns 404 when conversation not found`() {
        doReturn(emptyList<Conversation>()).whenever(conversationRepository)
            .getConversations(eq(WebSocketChannel.ANONYMOUS_USER), anyOrNull(), any(), any())

        val response = rest.getForEntity(
            "/assistants/my-agent/conversations/unknown",
            Any::class.java,
        )
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get returns 404 when assistant not found`() {
        val response = rest.getForEntity(
            "/assistants/unknown/conversations/conv-001",
            Any::class.java,
        )
        assertEquals(404, response.statusCode.value())
    }
}
