package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.channel.ChannelRegistry
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class ContextFactoryTest {
    private val channelRegistry = mock<ChannelRegistry>()
    private val llmFactory = mock<LLMFactory>()
    private val toolRegistry = mock<ToolRegistry>()
    private val skillRegistry = mock<SkillRegistry>()
    private val commandRegistry = mock<CommandRegistry>()
    private val jsonMapper = JsonMapper()
    private val factory = ContextFactory(
        toolRegistry,
        channelRegistry,
        llmFactory,
        commandRegistry,
        skillRegistry,
        jsonMapper
    )

    private val llm = mock<LLM>()

    @BeforeEach
    fun setUp() {
        doReturn(llm).whenever(llmFactory).create(any())
    }

    @Test
    fun create() {
        // GIVEN
        val home = File("target")
        val config = mapOf(
            "llm" to mapOf(
                "type" to "deepseek",
                "foo" to "bar"
            )
        )

        // WHEN
        val context = factory.create(home, config)

        // THEN
        verify(llmFactory).create("deepseek")

        assertEquals(home, context.home)
        assertEquals(llm, context.llm)
        assertEquals(config, context.config)
        assertEquals(commandRegistry, context.commandRegistry)
        assertEquals(toolRegistry, context.toolRegistry)
        assertEquals(jsonMapper, context.jsonMapper)
        assertEquals(skillRegistry, context.skillRegistry)

        verify(toolRegistry, times(10)).register(any())
    }

    @Test
    fun `create - no llm config`() {
        // GIVEN
        val home = File("target")
        val config = mapOf(
            "foo" to mapOf(
                "type" to "deepseek",
                "foo" to "bar"
            )
        )

        // WHEN
        val context = factory.create(home, config)

        // THEN
        verify(llmFactory).create("")
        assertEquals(llm, context.llm)
    }

    @Test
    fun `create - no llm-type config`() {
        // GIVEN
        val home = File("target")
        val config = mapOf(
            "llm" to mapOf(
                "foo" to "bar"
            )
        )

        // WHEN
        val context = factory.create(home, config)

        // THEN
        verify(llmFactory).create("")
        assertEquals(llm, context.llm)
    }

    @Test
    fun `create - empty llm-type config`() {
        // GIVEN
        val home = File("target")
        val config = mapOf(
            "llm" to mapOf(
                "foo" to "bar",
                "type" to ""
            )
        )

        // WHEN
        val context = factory.create(home, config)

        // THEN
        verify(llmFactory).create("")
        assertEquals(llm, context.llm)
    }
}
