package com.wutsi.kokibot.service.heartbeat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertTrue

class HeartbeatTest {
    private val assistant = mock<Assistant>()
    private val heartbeat = Heartbeat(assistant)
    private val context = Context(
        home = File(this::class.java.getResource("/home/007")!!.path),
        llm = mock(),
    )

    @Test
    fun id() {
        assertEquals(Heartbeat.ID, heartbeat.id())
    }

    @Test
    fun health() {
        val health = heartbeat.health()
        assertTrue(health.up)
        assertEquals(heartbeat.id(), health.id)
    }

    @Test
    fun tick() {
        doReturn(Message("Done")).whenever(assistant).process(any(), any())

        heartbeat.init(mapOf("" to ""), context)
        heartbeat.tick()

        val msg = argumentCaptor<Message>()
        verify(assistant).process(msg.capture(), eq(false))

        assertEquals(Role.SYSTEM, msg.firstValue.role)
        assertEquals("This is the heartbeat job\n", msg.firstValue.text)
        assertEquals(heartbeat.id(), msg.firstValue.userId)
    }

    @Test
    fun `tick - no HEARTBEAT file`() {
        doReturn(Message("Done")).whenever(assistant).process(any(), any())

        val ctx = Context(
            home = File("target/test-data/heartbeat"),
            llm = mock(),
        )
        heartbeat.init(mapOf("" to ""), ctx)
        heartbeat.tick()

        verify(assistant, never()).process(any(), any())
    }
}
