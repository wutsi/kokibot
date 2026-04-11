package com.wutsi.kokibot.tools

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ToolNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File

class ToolRegistryTest {
    val home = File("target/test-data/tool-registry-test")
    val meta1 = ToolMetadata(name = "tool1")
    val meta2 = ToolMetadata(name = "tool2")
    val tool1 = mock<Tool>()
    val tool2 = mock<Tool>()
    val context = Context(
        home = home,
        llm = mock()
    )
    val registry = ToolRegistry()

    @BeforeEach
    fun setup() {
        home.deleteRecursively()

        doReturn(meta1).whenever(tool1).metadata()
        doReturn(meta2).whenever(tool2).metadata()
    }

    @Test
    fun init() {
        // GIVEN
        val config1 = mapOf("key1" to "value1")
        val file = File(home.absolutePath + "/config/tools/${meta1.name}.json")
        file.parentFile.mkdirs()
        file.writeText(JsonMapper().writeValueAsString(config1))

        registry.register(tool1)
        registry.register(tool2)

        // WHEN
        registry.init(context)

        // GIVEN
        assertEquals(2, registry.all().size)
        verify(tool1).init(config1, context)
        verify(tool2).init(emptyMap<String, Any>(), context)
    }

    @Test
    fun destroy() {
        // GIVEN
        registry.register(tool1)
        registry.register(tool2)
        registry.init(context)

        // WHEN
        registry.destroy()

        // THEN
        verify(tool1).destroy()
        verify(tool2).destroy()
    }

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
