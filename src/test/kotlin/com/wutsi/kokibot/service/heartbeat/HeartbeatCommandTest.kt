package com.wutsi.kokibot.service.heartbeat

import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class HeartbeatCommandTest {
    private val heartbeat = mock<Heartbeat>()
    private val context = Context(
        home = File("target/test-data/heartbeat-command"),
        llm = mock(),
        heartbeat = heartbeat,
    )
    private val command = HeartbeatCommand()

    @Test
    fun metadata() {
        val meta = command.metadata()
        assertEquals("/heartbeat", meta.name)
    }

    @Test
    fun exec() {
        val result = command.exec("", context)
        assertEquals("Heartbeat triggered", result)
        verify(heartbeat).tick()
    }
}
