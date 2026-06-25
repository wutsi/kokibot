package com.wutsi.kokibot.service.heartbeat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeartbeatTest {
    private val heartbeat = Heartbeat()
    private val context = Context(
        home = File(this::class.java.getResource("/home/007")!!.path),
        llm = mock(),
        assistant = mock(),
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
        doReturn(Message("Done")).whenever(context.assistant).process(any(), anyOrNull())

        heartbeat.init(mapOf("frequency" to "30m"), context)
        heartbeat.tick()

        val msg = argumentCaptor<Message>()
        verify(context.assistant).process(msg.capture(), anyOrNull())

        assertEquals(Role.SYSTEM, msg.firstValue.role)
//        assertEquals("Run every hour", msg.firstValue.text)
        assertEquals(heartbeat.id(), msg.firstValue.channelId)
        assertEquals(System.getProperty("user.name"), msg.firstValue.userId)
    }

    @Test
    fun `tick - no HEARTBEAT file`() {
        doReturn(Message("Done")).whenever(context.assistant).process(any(), anyOrNull())

        val ctx = Context(
            home = File("target/test-data/heartbeat"),
            llm = mock(),
        )
        heartbeat.init(mapOf("frequency" to 30), ctx)
        heartbeat.tick()

        verify(context.assistant, never()).process(any(), anyOrNull())
    }

    @Test
    fun `tick - disabled`() {
        doReturn(Message("Done")).whenever(context.assistant).process(any(), anyOrNull())

        heartbeat.init(mapOf("frequency" to "30m", "enabled" to false), context)
        heartbeat.tick()

        verify(context.assistant, never()).process(any(), anyOrNull())
    }

    @Test
    fun `init - defaults`() {
        heartbeat.init(emptyMap<String, Any>(), context)

        assertTrue(heartbeat.isEnabled())
        assertEquals(Heartbeat.DEFAULT_FREQUENCY, heartbeat.getFrequency())
    }

    @Test
    fun `apply - enabled false`() {
        heartbeat.init(mapOf("frequency" to 30), context)

        heartbeat.apply("enabled", false)

        assertFalse(heartbeat.isEnabled())
    }

    @Test
    fun `apply - enabled true`() {
        heartbeat.init(mapOf("frequency" to 30, "enabled" to false), context)

        heartbeat.apply("enabled", true)

        assertTrue(heartbeat.isEnabled())
    }

    @Test
    fun `apply - frequency`() {
        heartbeat.init(mapOf("frequency" to "30m"), context)

        heartbeat.apply("frequency", "60m")

        assertEquals("60m", heartbeat.getFrequency())
    }

    @Test
    fun `apply - instructions`() {
        heartbeat.init(mapOf("frequency" to 30), context)

        heartbeat.apply("instructions", "Run every hour")

        assertEquals("Run every hour", heartbeat.getInstructions())
    }

    @Test
    fun `apply - unknown key`() {
        heartbeat.init(mapOf("frequency" to 30), context)

        assertThrows<ConfigurationException> {
            heartbeat.apply("unknown", "value")
        }
    }
}
