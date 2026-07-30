package com.wutsi.kokibot.service.inbox

import com.fasterxml.jackson.annotation.JsonInclude
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File

class InboxTest {
    private val home = File("target/test-data/inbox")
    private val jsonMapper = JsonMapper.builderWithJackson2Defaults()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .build()
    private val context = Context(home = home, llm = mock(), jsonMapper = jsonMapper)
    private val inbox = Inbox()

    @BeforeEach
    fun setup() {
        inbox.init(emptyMap<String, Any>(), context)
    }

    @AfterEach
    fun tearDown() {
        home.deleteRecursively()
    }

    @Test
    fun id() {
        assertEquals("service:inbox", inbox.id())
    }

    @Test
    fun `init creates inbox directories`() {
        listOf(Inbox.PENDING, Inbox.PROCESSING, Inbox.DONE, Inbox.FAILED, Inbox.ORPHANED, Inbox.CANCEL).forEach { state ->
            assert(File(home, "inbox/$state").isDirectory) { "Expected inbox/$state to exist" }
        }
    }

    @Test
    fun `init moves processing messages to orphaned`() {
        // Simulate a message stuck in processing from a previous run
        inbox.submit(message("msg-stuck"))
        inbox.poll()
        assertEquals(1, processingCount())

        // Re-init simulates a server restart
        inbox.init(emptyMap<String, Any>(), context)

        assertEquals(0, processingCount())
        assertEquals(1, orphanedCount())
    }

    @Test
    fun submit() {
        val message = message("msg-1")

        val result = inbox.submit(message)

        assertEquals(message.id, result.id)
        assertEquals(message.channelId, result.channelId)
        assertEquals(message.userId, result.userId)
        assertEquals(message.text, result.text)
        assertEquals(message.filePaths, result.filePaths)
        assertEquals(message.subject, result.subject)
        assertNotNull(result.submittedAt)
        assertNull(result.processedAt)
        assertNull(result.completedAt)
        assertNull(result.response)
        assertNull(result.error)

        assertEquals(1, File(home, "inbox/${Inbox.PENDING}").listFiles()?.size)
    }

    @Test
    fun `submit - invokes onSubmit callback`() {
        var called = false
        inbox.onSubmit { called = true }

        inbox.submit(message("msg-1"))

        assertTrue(called)
    }

    @Test
    fun `poll - returns null when inbox is empty`() {
        assertNull(inbox.poll())
    }

    @Test
    fun `poll - returns oldest pending message and moves it to processing`() {
        inbox.submit(message("msg-1"))
        Thread.sleep(10)
        inbox.submit(message("msg-2"))

        val result = inbox.poll()

        assertNotNull(result)
        assertEquals("msg-1", result!!.id)
        assertNotNull(result.processedAt)

        assertEquals(1, pendingCount())
        assertEquals(1, processingCount())
    }

    @Test
    fun `poll - returns null after all messages consumed`() {
        inbox.submit(message("msg-1"))
        inbox.poll()

        assertNull(inbox.poll())
    }

    @Test
    fun complete() {
        inbox.submit(message("msg-1"))
        val polled = inbox.poll()!!

        inbox.complete(polled.id, "The answer is 42")

        assertEquals(0, processingCount())
        assertEquals(1, doneCount())

        val done = readFirst(Inbox.DONE)
        assertEquals("The answer is 42", done.response)
        assertNotNull(done.completedAt)
        assertNull(done.error)
    }

    @Test
    fun fail() {
        inbox.submit(message("msg-1"))
        val polled = inbox.poll()!!

        inbox.fail(polled.id, "Something went wrong")

        assertEquals(0, processingCount())
        assertEquals(1, failedCount())

        val failed = readFirst(Inbox.FAILED)
        assertEquals("Something went wrong", failed.error)
        assertNotNull(failed.completedAt)
        assertNull(failed.response)
    }

    @Test
    fun `complete - does nothing for unknown id`() {
        inbox.complete("unknown-id", "response")

        assertEquals(0, doneCount())
    }

    @Test
    fun `fail - does nothing for unknown id`() {
        inbox.fail("unknown-id", "error")

        assertEquals(0, failedCount())
    }

    @Test
    fun `cancel - marks message as cancelled`() {
        assertTrue(!inbox.isCancelled("msg-1"))

        inbox.cancel("msg-1")

        assertTrue(inbox.isCancelled("msg-1"))
    }

    @Test
    fun `complete - clears cancel marker`() {
        inbox.submit(message("msg-1"))
        val polled = inbox.poll()!!
        inbox.cancel(polled.id)

        inbox.complete(polled.id, "The answer is 42")

        assertTrue(!inbox.isCancelled(polled.id))
    }

    @Test
    fun `fail - clears cancel marker`() {
        inbox.submit(message("msg-1"))
        val polled = inbox.poll()!!
        inbox.cancel(polled.id)

        inbox.fail(polled.id, "Something went wrong")

        assertTrue(!inbox.isCancelled(polled.id))
    }

    @Test
    fun `processingCount - returns number of messages in processing state`() {
        assertEquals(0, inbox.processingCount())

        inbox.submit(message("msg-1"))
        inbox.submit(message("msg-2"))
        inbox.poll()

        assertEquals(1, inbox.processingCount())

        inbox.poll()

        assertEquals(2, inbox.processingCount())
    }

    private fun message(id: String) = Message(
        id = id,
        channelId = "channel:telegram",
        userId = "user123",
        text = "Hello",
        subject = "Test",
        filePaths = listOf("/tmp/file.txt"),
        role = Role.USER,
    )

    private fun pendingCount() = File(home, "inbox/${Inbox.PENDING}").listFiles()?.size ?: 0
    private fun processingCount() = File(home, "inbox/${Inbox.PROCESSING}").listFiles()?.size ?: 0
    private fun doneCount() = File(home, "inbox/${Inbox.DONE}").listFiles()?.size ?: 0
    private fun failedCount() = File(home, "inbox/${Inbox.FAILED}").listFiles()?.size ?: 0
    private fun orphanedCount() = File(home, "inbox/${Inbox.ORPHANED}").listFiles()?.size ?: 0

    private fun readFirst(state: String): InboxMessage {
        val file = File(home, "inbox/$state").listFiles()!!.first()
        return jsonMapper.readValue(file.readText(), InboxMessage::class.java)
    }
}
