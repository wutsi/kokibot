package com.wutsi.kokibot.channel

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class ChannelRegistryTest {
    private val assistant = mock<Assistant>()
    private val context = mock<Context>()
    private val factory = mock<ChannelFactory>()
    private val registry = ChannelRegistry(factory)
    private val channel1 = mock<Channel>()
    private val channel2 = mock<Channel>()
    private val config = mapOf(
        "channels" to listOf(
            mapOf("type" to "test1"),
            mapOf("type" to "test2"),
        )
    )

    @BeforeEach
    fun setup() {
        doReturn("ch1").whenever(channel1).id()
        doReturn("ch2").whenever(channel2).id()

        doReturn(channel1).whenever(factory).create("test1", assistant)
        doReturn(channel2).whenever(factory).create("test2", assistant)
    }

    @Test
    fun init() {
        registry.init(config, context, assistant)

        verify(channel1).init(mapOf("type" to "test1"), context)
        verify(channel2).init(mapOf("type" to "test2"), context)
    }

    @Test
    fun `init - missing type`() {
        val config = mapOf(
            "channels" to listOf(
                mapOf("__type__" to "test1"),
                mapOf("type" to "test2"),
            )
        )
        registry.init(config, context, assistant)

        verify(channel1, never()).init(any(), any())
        verify(channel2).init(mapOf("type" to "test2"), context)
    }

    @Test
    fun `init - channel initialization error`() {
        doThrow(IllegalArgumentException::class).whenever(channel1).init(any(), any())

        registry.init(config, context, assistant)

        assertEquals(listOf(channel2), registry.all())
    }

    @Test
    fun `init - channels not list`() {
        val config = mapOf(
            "channels" to mapOf("type" to "test1"),
        )

        registry.init(config, context, assistant)
    }

    @Test
    fun all() {
        registry.init(config, context, assistant)

        assertEquals(listOf(channel1, channel2), registry.all())
    }

    @Test
    fun get() {
        registry.init(config, context, assistant)
        val channel = registry.get("ch1")

        assertEquals(channel1, channel)
    }

    @Test
    fun `get - not found`() {
        registry.init(config, context, assistant)

        assertThrows<ChannelNotFoundException> { registry.get("xxx") }
    }
}
