package com.wutsi.kokibot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AssistantRegistryTest {
    private val registry = AssistantRegistry()

    @Test
    fun `register and get`() {
        val assistant = Assistant("Foo")
        registry.register(assistant)

        assertEquals(assistant, registry.get("Foo"))
    }

    @Test
    fun `get is case-insensitive`() {
        val assistant = Assistant("Foo")
        registry.register(assistant)

        assertEquals(assistant, registry.get("foo"))
        assertEquals(assistant, registry.get("FOO"))
    }

    @Test
    fun `register overwrites previous assistant with same name`() {
        val first = Assistant("Foo")
        val second = Assistant("foo")

        registry.register(first)
        registry.register(second)

        assertEquals(second, registry.get("Foo"))
    }

    @Test
    fun `get unknown assistant throws`() {
        assertThrows<AssistantNotFoundException> { registry.get("xxx") }
    }
}
