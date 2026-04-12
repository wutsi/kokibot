package com.wutsi.kokibot.skill

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.tools.ToolMetadata
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class SkillCommandTest {
    private val skillRegistry = mock<SkillRegistry>()
    private val context = Context(
        home = File("/target"),
        llm = mock<LLM>(),
        skillRegistry = skillRegistry,
    )
    private val cmd = SkillCommand()

    @Test
    fun metadata() {
        assertEquals(SkillCommand.NAME, cmd.metadata().name)
    }

    @Test
    fun `exec list`() {
        val skill1 = mock<Skill>()
        val skill2 = mock<Skill>()
        doReturn(SkillMetadata(name = "skill1")).whenever(skill1).metadata
        doReturn(SkillMetadata(name = "skill2")).whenever(skill2).metadata
        doReturn(listOf(skill1, skill2)).whenever(skillRegistry).all()

        val result = cmd.exec("", context)

        assertEquals(
            """
                2 skill(s) found
                - skill1
                - skill2
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec skill`() {
        val skill = Skill(
            metadata = SkillMetadata(
                name = "skill1",
                description = "description of skill1",
                requiredBins = listOf("bin1", "bin2"),
                requiredEnv = listOf("env1", "env2"),
                tools = listOf(
                    ToolMetadata(name = "tool1", description = "description of tool1"),
                    ToolMetadata(name = "tool2", description = "description of tool2"),
                )
            )
        )
        doReturn(skill).whenever(skillRegistry).get("skill1")

        val result = cmd.exec("skill1", context)

        assertEquals(
            """
                *Skill:* skill1

                *Description:* description of skill1

                *Required Bin:* - \`bin1\`,- \`bin2\`

                *Required Env:* - \`env1\`,- \`env2\`

                *Tools:*
                - tool1
                - tool2
            """.trimIndent(),
            result
        )
    }
}
