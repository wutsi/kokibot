package com.wutsi.kokibot.command

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.exception.CommandNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class CommandRegistryTest {
    val registry = CommandRegistry()

    @Test
    fun `register and get - name with slash`() {
        val cmd = mock<Command>()
        doReturn("/foo").whenever(cmd).name()

        registry.register(cmd)
        val result = registry.get("/foo")

        assertEquals(cmd, result)
    }

    @Test
    fun `register and get - name without slash`() {
        val cmd = mock<Command>()
        doReturn("foo").whenever(cmd).name()

        registry.register(cmd)
        val result = registry.get("/foo")

        assertEquals(cmd, result)
    }

    @Test
    fun `get invalid command`() {
        assertThrows<CommandNotFoundException> { registry.get("xxx") }
    }
}
