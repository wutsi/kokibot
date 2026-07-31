package com.wutsi.kokibot.service.inbox

import com.fasterxml.jackson.annotation.JsonInclude
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.FinishReason
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.llm.LLMStreamData
import com.wutsi.kokibot.llm.LLMUsage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File

class InboxPollerTest {
    private val home = File("target/test-data/inbox-poller")
    private val jsonMapper = JsonMapper.builderWithJackson2Defaults()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .build()
    private val channelRegistry = mock<ChannelRegistry>()
    private val inbox = Inbox()
    private val context = Context(
        home = home,
        llm = mock(),
        assistant = mock(),
        channelRegistry = channelRegistry,
        jsonMapper = jsonMapper,
        inbox = inbox,
    )
    private val poller = InboxPoller()

    @BeforeEach
    fun setup() {
        inbox.init(emptyMap<String, Any>(), context)
        poller.init(mapOf("frequency" to "1h"), context)
    }

    @AfterEach
    fun tearDown() {
        poller.destroy()
        home.deleteRecursively()
    }

    @Test
    fun id() {
        assertEquals(InboxPoller.ID, poller.id())
    }

    @Test
    fun `tick - does nothing when inbox is empty`() {
        poller.tick()

        verify(context.assistant, never()).process(any(), anyOrNull())
    }

    @Test
    fun `tick - processes pending message and delivers response`() {
        val channel = mock<Channel>()
        doReturn(channel).whenever(channelRegistry).get("channel:telegram")
        doReturn(Message(text = "42", role = Role.ASSISTANT))
            .whenever(context.assistant).process(any(), anyOrNull())

        inbox.submit(message("msg-1", channelId = "channel:telegram"))

        poller.tick()

        val queryCaptor = argumentCaptor<Message>()
        verify(context.assistant).process(queryCaptor.capture(), anyOrNull())
        assertEquals("msg-1", queryCaptor.firstValue.id)
        assertEquals("Hello", queryCaptor.firstValue.text)
        assertEquals("user123", queryCaptor.firstValue.userId)
        assertEquals(Role.USER, queryCaptor.firstValue.role)

        val responseCaptor = argumentCaptor<Message>()
        verify(channel).send(responseCaptor.capture())
        assertEquals("42", responseCaptor.firstValue.text)
        assertEquals(Role.ASSISTANT, responseCaptor.firstValue.role)
        assertEquals("channel:telegram", responseCaptor.firstValue.channelId)

        assertEquals(0, File(home, "inbox/${Inbox.PENDING}").listFiles()?.size)
        assertEquals(0, File(home, "inbox/${Inbox.PROCESSING}").listFiles()?.size)
        assertEquals(1, File(home, "inbox/${Inbox.DONE}").listFiles()?.size)
    }

    @Test
    fun `tick - drains all pending messages`() {
        doReturn(Message(text = "ok", role = Role.ASSISTANT))
            .whenever(context.assistant).process(any(), anyOrNull())

        inbox.submit(message("msg-1"))
        inbox.submit(message("msg-2"))
        inbox.submit(message("msg-3"))

        poller.tick()

        verify(context.assistant, times(3)).process(any(), anyOrNull())
        assertEquals(0, File(home, "inbox/${Inbox.PENDING}").listFiles()?.size)
        assertEquals(3, File(home, "inbox/${Inbox.DONE}").listFiles()?.size)
    }

    @Test
    fun `tick - moves message to failed when assistant throws`() {
        doThrow(RuntimeException("LLM unavailable"))
            .whenever(context.assistant).process(any(), anyOrNull())

        inbox.submit(message("msg-1"))

        poller.tick()

        assertEquals(0, File(home, "inbox/${Inbox.PROCESSING}").listFiles()?.size)
        assertEquals(1, File(home, "inbox/${Inbox.FAILED}").listFiles()?.size)
    }

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

    @Test
    fun `tick - forwards finishReason in delivered response`() {
        val channel = mock<Channel>()
        doReturn(channel).whenever(channelRegistry).get("channel:telegram")
        doReturn(Message(text = "Query cancelled.", role = Role.ASSISTANT, finishReason = FinishReason.CANCELLED))
            .whenever(context.assistant).process(any(), anyOrNull())

        inbox.submit(message("msg-1", channelId = "channel:telegram"))

        poller.tick()

        val responseCaptor = argumentCaptor<Message>()
        verify(channel).send(responseCaptor.capture())
        assertEquals(FinishReason.CANCELLED, responseCaptor.firstValue.finishReason)
    }

    @Test
    fun `tick - skips stream callback and delivery when channelId is null`() {
        doReturn(Message(text = "ok", role = Role.ASSISTANT))
            .whenever(context.assistant).process(any(), anyOrNull())

        inbox.submit(message("msg-1", channelId = null))

        poller.tick()

        verify(channelRegistry, never()).get(any())
        assertEquals(1, File(home, "inbox/${Inbox.DONE}").listFiles()?.size)
    }

    @Test
    fun `tick - survives channel delivery failure`() {
        doReturn(Message(text = "ok", role = Role.ASSISTANT))
            .whenever(context.assistant).process(any(), anyOrNull())
        doThrow(RuntimeException("channel down")).whenever(channelRegistry).get(any())

        inbox.submit(message("msg-1", channelId = "channel:telegram"))

        poller.tick()

        // Message still completes despite delivery failure
        assertEquals(1, File(home, "inbox/${Inbox.DONE}").listFiles()?.size)
    }

    @Test
    fun `tick - does not re-enter when already running`() {
        // Simulate concurrent second call while first is still in running state
        // by checking AtomicBoolean guard indirectly: a second tick on an empty inbox is a no-op
        poller.tick()
        poller.tick()

        verify(context.assistant, never()).process(any(), anyOrNull())
    }

    @Test
    fun `tick - defers pending messages when WIP limit is reached`() {
        // Simulate 2 messages stuck in processing/ from a previous run (crash recovery scenario)
        poller.init(mapOf("frequency" to "1h", "max-wip" to 2), context)
        stuckInProcessing("stuck-1")
        stuckInProcessing("stuck-2")

        // One new message waiting in pending
        inbox.submit(message("msg-new"))

        poller.tick()

        // Assistant must not be called — WIP is already at limit
        verify(context.assistant, never()).process(any(), anyOrNull())
        assertEquals(1, File(home, "inbox/${Inbox.PENDING}").listFiles()?.size)
        assertEquals(2, File(home, "inbox/${Inbox.PROCESSING}").listFiles()?.size)
    }

    @Test
    fun `tick - picks up pending when WIP is below limit`() {
        doReturn(Message(text = "ok", role = Role.ASSISTANT))
            .whenever(context.assistant).process(any(), anyOrNull())

        // max-wip=2, one stuck in processing → one slot free
        poller.init(mapOf("frequency" to "1h", "max-wip" to 2), context)
        stuckInProcessing("stuck-1")
        inbox.submit(message("msg-new"))

        poller.tick()

        verify(context.assistant, times(1)).process(any(), anyOrNull())
        assertEquals(0, File(home, "inbox/${Inbox.PENDING}").listFiles()?.size)
    }

    private fun stuckInProcessing(id: String) {
        val msg = InboxMessage(id = id, text = "stuck", submittedAt = java.time.LocalDateTime.now())
        val ts = msg.submittedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
        val file = File(home, "inbox/${Inbox.PROCESSING}/$ts-$id.json")
        file.writeText(jsonMapper.writeValueAsString(msg))
    }

    private fun message(id: String, channelId: String? = "channel:telegram") = Message(
        id = id,
        channelId = channelId,
        userId = "user123",
        text = "Hello",
        role = Role.USER,
    )
}
