package com.wutsi.kokibot.tools

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.exception.ToolNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class ToolRegistryTest {
    val registry = ToolRegistry()

    @Test
    fun register() {
        val meta = ToolMetadata(
            name = "test",
            description = "test tool",
            parameters = emptyList(),
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        registry.register(tool)

        val result = registry.get("TEST")
        assertEquals(result, tool)
    }

    @Test
    fun `tool not found`() {
        assertThrows<ToolNotFoundException> { registry.get("xxx") }
    }
}
