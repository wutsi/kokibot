package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.channel.ChannelFactory
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class BootstrapTest {
    private val channelFactory = mock<ChannelFactory>()
    private val llmFactory = mock<LLMFactory>()
    private val toolRegistry = mock<ToolRegistry>()
    private val jsonMapper = JsonMapper()
    private val bootstrap = Bootstrap(channelFactory, llmFactory, toolRegistry, jsonMapper)
    private val channel = mock<Channel>()
    private val llm = mock<LLM>()

    @BeforeEach
    fun setup() {
        doReturn(channel).whenever(channelFactory).create(any(), any())
        doReturn(llm).whenever(llmFactory).create(any())
    }

    @Test
    fun destroy() {
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.destroy()

        verify(channel).destroy()
    }

    @Test
    fun init() {
        val home = getResourceFile("/home/007")
        bootstrap.init(home)

        assertEquals(1, bootstrap.channels.size)
        assertEquals(channel, bootstrap.channels[0])
        verify(channel).init(any())
        verify(llm).init(any(), eq(toolRegistry))
        verify(toolRegistry, times(12)).register(any())
    }

    @Test
    fun `channel - none`() {
        bootstrap.init(getResourceFile("/home/channel-none"))
        assertEquals(0, bootstrap.channels.size)
    }

    @Test
    fun `channel - no type`() {
        assertThrows<ConfigurationException> {
            bootstrap.init(getResourceFile("/home/channel-no-type"))
        }
    }

    @Test
    fun `llm - none`() {
        assertThrows<ConfigurationException> {
            bootstrap.init(getResourceFile("/home/llm-none"))
        }
    }

    @Test
    fun `llm - no type`() {
        assertThrows<ConfigurationException> {
            bootstrap.init(getResourceFile("/home/llm-no-type"))
        }
    }

    @Test
    fun `llm - not object`() {
        assertThrows<ConfigurationException> {
            bootstrap.init(getResourceFile("/home/llm-not-object"))
        }
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
