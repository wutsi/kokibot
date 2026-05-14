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
    fun `register duplicate name`() {
        val first = Assistant("Foo")
        val second = Assistant("foo")

        registry.register(first)

        assertThrows<AssistantAlreadyRegisteredException> {
            registry.register(second)
        }
    }

    @Test
    fun `get unknown assistant throws`() {
        assertThrows<AssistantNotFoundException> { registry.get("xxx") }
    }

    @Test
    fun all() {
        val first = Assistant("Foo")
        val second = Assistant("bar")

        registry.register(first)
        registry.register(second)

        assertEquals(2, registry.all().size)
    }
}
