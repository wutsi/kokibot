package com.wutsi.kokibot.tools.skill

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillActivationToolTest {
    private val tool = SkillActivationTool()
    private val skillRegistry = mock<SkillRegistry>()
    private val skill1 = mock<Skill>()
    private val skill2 = mock<Skill>()

    private val meta1 = SkillMetadata(
        name = "skill1",
        description = "Description of the skill",
        home = File("target"),
    )
    private val body1 = "This is the content of the skill1"

    private val meta2 = SkillMetadata(
        name = "skill2",
        description = "Description of the skill",
        home = File("target"),
    )
    private val body2 = "This is the content of the skill2"

    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        skillRegistry = skillRegistry,
    )

    @BeforeEach
    fun setUp() {
        doReturn(meta1).whenever(skill1).metadata
        doReturn(body1).whenever(skill1).body
        doReturn(skill1).whenever(skillRegistry).get("skill1")
        skill1.init(mapOf("" to "xx"), context)

        doReturn(meta2).whenever(skill2).metadata
        doReturn(body2).whenever(skill2).body
        doReturn(skill2).whenever(skillRegistry).get("skill2")
        skill2.init(mapOf("" to "xx"), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(SkillActivationTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)

        assertEquals("skills", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `exec - activate`() {
        doReturn(true).whenever(skill1).activate()

        tool.init(mapOf("" to "xx"), context)
        val result = tool.exec(mapOf("skills" to "skill1"))

        assertTrue(result.contains("skill1"))
        assertTrue(result.contains(skill1.body))

        assertFalse(result.contains("skill2"))
        assertFalse(result.contains(skill2.body))
    }

    @Test
    fun `exec - activate multiple`() {
        doReturn(true).whenever(skill1).activate()
        doReturn(true).whenever(skill2).activate()

        tool.init(mapOf("" to "xx"), context)
        val result = tool.exec(mapOf("skills" to "skill1, skill2"))

        assertTrue(result.contains("skill1"))
        assertTrue(result.contains(skill1.body))

        assertTrue(result.contains("skill2"))
        assertTrue(result.contains(skill2.body))
    }

    @Test
    fun `exec - not-active`() {
        doReturn(false).whenever(skill1).activate()

        tool.init(mapOf("" to "xx"), context)
        val result = tool.exec(mapOf("skills" to "skill1"))

        assertFalse(result.contains(skill1.body))
    }

    @Test
    fun `exec - skill not found`() {
        tool.init(mapOf("" to "xx"), context)
        val result = tool.exec(mapOf("skills" to "unknown_skill"))

        assertTrue(result.contains("Unable to activate skill"))
        assertTrue(result.contains("unknown_skill"))
    }

    @Test
    fun `exec - empty skills parameter`() {
        tool.init(mapOf("" to "xx"), context)

        try {
            tool.exec(mapOf("skills" to ""))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `exec - missing skills parameter`() {
        tool.init(mapOf("" to "xx"), context)

        try {
            tool.exec(emptyMap<String, Any>())
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing required argument") == true)
        }
    }

    @Test
    fun `statusText - single skill`() {
        tool.init(mapOf("" to "xx"), context)

        val toolCalls = listOf(
            com.wutsi.kokibot.llm.LLMToolCall(
                id = "1",
                name = "skill_activate",
                arguments = mapOf("skills" to "skill1")
            )
        )

        val result = tool.statusText(toolCalls)
        assertEquals("Activating skill: skill1", result)
    }

    @Test
    fun `statusText - multiple skills`() {
        tool.init(mapOf("" to "xx"), context)

        val toolCalls = listOf(
            com.wutsi.kokibot.llm.LLMToolCall(
                id = "1",
                name = "skill_activate",
                arguments = mapOf("skills" to "skill1,skill2")
            ),
            com.wutsi.kokibot.llm.LLMToolCall(
                id = "2",
                name = "skill_activate",
                arguments = mapOf("skills" to "skill3")
            )
        )

        val result = tool.statusText(toolCalls)
        assertEquals("Activating skills: skill1,skill2,skill3", result)
    }

    @Test
    fun `statusText - more than 5 skills`() {
        tool.init(mapOf("" to "xx"), context)

        val toolCalls = listOf(
            com.wutsi.kokibot.llm.LLMToolCall(
                id = "1",
                name = "skill_activate",
                arguments = mapOf("skills" to "skill1,skill2,skill3,skill4,skill5,skill6,skill7")
            )
        )

        val result = tool.statusText(toolCalls)
        assertEquals("Activating skills: skill1,skill2,skill3,skill4,skill5 and 2 more", result)
    }
}
