package com.wutsi.kokibot.channel

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class ChannelRegistryTest {
    private val home = File("target/test-data/channel-registry")
    private val context = mock<Context>()
    private val factory = mock<ChannelFactory>()
    private val registry = ChannelRegistry(factory)
    private val channel1 = mock<Channel>()
    private val channel2 = mock<Channel>()

    @BeforeEach
    fun setup() {
        if (home.exists()) {
            home.deleteRecursively()
        }
        val dir = File(home, "config/channels")
        dir.mkdirs()

        File(dir, "test1.json").writeText("""{ "type": "test1" }""")
        File(dir, "test2.json").writeText("""{ "type": "test2" }""")

        doReturn(home).whenever(context).home
        doReturn("ch1").whenever(channel1).id()
        doReturn("ch2").whenever(channel2).id()
        doReturn(channel1).whenever(factory).create("test1")
        doReturn(channel2).whenever(factory).create("test2")
    }

    @Test
    fun init() {
        registry.init(context)

        verify(channel1).init(mapOf("type" to "test1"), context)
        verify(channel2).init(mapOf("type" to "test2"), context)
    }

    @Test
    fun `init - missing type`() {
        File(home, "config/channels/no-type.json").writeText("""{ "foo": "bar" }""")

        registry.init(context)

        verify(channel1).init(mapOf("type" to "test1"), context)
        verify(channel2).init(mapOf("type" to "test2"), context)
    }

    @Test
    fun `init - channel initialization error`() {
        doThrow(IllegalArgumentException::class).whenever(channel1).init(any(), any())

        registry.init(context)

        assertEquals(listOf(channel2), registry.all())
    }

    @Test
    fun `init - no channels directory`() {
        File(home, "config/channels").deleteRecursively()

        registry.init(context)

        assertEquals(emptyList(), registry.all())
    }

    @Test
    fun destroy() {
        registry.init(context)
        registry.destroy()

        verify(channel1).destroy()
        verify(channel2).destroy()
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `destroy - ignore channel failure`() {
        doThrow(RuntimeException::class).whenever(channel1).destroy()

        registry.init(context)
        registry.destroy()

        verify(channel1).destroy()
        verify(channel2).destroy()
        assertEquals(0, registry.all().size)
    }

    @Test
    fun all() {
        registry.init(context)

        assertEquals(listOf(channel1, channel2), registry.all())
    }

    @Test
    fun get() {
        registry.init(context)
        val channel = registry.get("ch1")

        assertEquals(channel1, channel)
    }

    @Test
    fun `get - not found`() {
        registry.init(context)

        assertThrows<ChannelNotFoundException> { registry.get("xxx") }
    }
}
