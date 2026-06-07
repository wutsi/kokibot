package com.wutsi.kokibot.tools.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendMessageToolTest {
    private val context = Context(
        home = File("target"),
        llm = mock(),
        channelRegistry = mock(),
    )
    private val tool = SendMessageTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(SendMessageTool.NAME, meta.name)
        assertEquals(4, meta.parameters.size)

        assertEquals("user_id", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("channel_id", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertTrue(meta.parameters[1].required)

        assertEquals("message", meta.parameters[2].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[2].type)
        assertTrue(meta.parameters[2].required)

        assertEquals("file_paths", meta.parameters[3].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[3].type)
        assertFalse(meta.parameters[3].required)
    }

    @Test
    fun exec() {
        // GIVEN
        val channel = mock<Channel>()
        doReturn(true).whenever(channel).send(any())
        doReturn(channel).whenever(context.channelRegistry).get(any())

        // WHEN
        tool.init(mapOf("" to "yy"), context)
        val result = tool.exec(
            mapOf(
                "user_id" to "ray.sponsible",
                "channel_id" to "telegram",
                "message" to "Hello, World!",
                "file_paths" to "/path/to/file1,/path/to/file2"
            )
        )

        // THEN
        verify(context.channelRegistry).get("channel:telegram")

        val msg = argumentCaptor<Message>()
        verify(channel).send(msg.capture())
        assertEquals("ray.sponsible", msg.firstValue.userId)
        assertEquals("Hello, World!", msg.firstValue.text)
        assertEquals(listOf("/path/to/file1", "/path/to/file2"), msg.firstValue.filePaths)

        assertEquals("SUCCESS. Message sent to ray.sponsible via telegram", result)
    }

    @Test
    fun `exec - delivery error`() {
        // GIVEN
        val channel = mock<Channel>()
        doReturn(channel).whenever(context.channelRegistry).get(any())
        doThrow(RuntimeException("failure")).whenever(channel).send(any())

        // WHEN
        tool.init(mapOf("" to "yy"), context)
        val result = tool.exec(
            mapOf(
                "user_id" to "ray.sponsible",
                "channel_id" to "telegram",
                "message" to "Hello, World!",
            )
        )

        // THEN
        verify(context.channelRegistry).get("channel:telegram")

        val msg = argumentCaptor<Message>()
        verify(channel).send(msg.capture())
        assertEquals("ray.sponsible", msg.firstValue.userId)
        assertEquals("Hello, World!", msg.firstValue.text)
        assertEquals(emptyList(), msg.firstValue.filePaths)

        assertEquals("Message was not sent to ray.sponsible via telegram. Error=failure", result)
    }

    @Test
    fun `exec - invalid channel`() {
        // GIVEN
        val channel = mock<Channel>()
        doReturn(channel).whenever(context.channelRegistry).get(any())

        doThrow(ChannelNotFoundException::class).whenever(channel).send(any())

        // WHEN
        tool.init(mapOf("" to "yy"), context)
        val result = tool.exec(
            mapOf(
                "user_id" to "123456",
                "channel_id" to "xxx",
                "message" to "Hello, World!",
            )
        )

        // THEN
        assertEquals("Message was not sent. The channel xxx is not available", result)
    }

    @Test
    fun `init - missing user_id`() {
        // WHEN
        tool.init(mapOf("" to "yy"), context)
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "channel_id" to "telegram",
                    "message" to "Hello, World!",
                )
            )
        }
    }

    @Test
    fun `init - missing channel_id`() {
        // WHEN
        tool.init(mapOf("" to "yy"), context)
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "user_id" to "11111",
                    "message" to "Hello, World!",
                )
            )
        }
    }

    @Test
    fun `init - missing message`() {
        // WHEN
        tool.init(mapOf("" to "yy"), context)
        assertThrows<IllegalArgumentException> {
            tool.exec(
                mapOf(
                    "user_id" to "11111",
                    "channel_id" to "messenger",
                )
            )
        }
    }

    @Test
    fun statusText() {
        val result = tool.statusText(
            listOf(
                LLMToolCall(
                    name = SendMessageTool.NAME,
                    arguments = mapOf(
                        "user_id" to "11111",
                        "channel_id" to "messenger",
                        "message" to "Hello, World!",
                    )
                )
            )
        )
        assertEquals("Sending message", result)
    }
}
