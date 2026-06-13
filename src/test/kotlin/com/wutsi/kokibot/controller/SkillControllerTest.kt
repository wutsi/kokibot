package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SkillControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun skills() {
        val skills = listOf(
            createSkill("crm", "CRM operations"),
            createSkill("weather", "Weather forecasting"),
        )
        doReturn(listOf(createBootstrap("007", skills = skills))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills", List::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(2, body.size)
        assertEquals("crm", (body[0] as Map<*, *>)["name"])
        assertEquals("CRM operations", (body[0] as Map<*, *>)["description"])
        assertEquals("weather", (body[1] as Map<*, *>)["name"])
        assertEquals("Weather forecasting", (body[1] as Map<*, *>)["description"])
    }

    @Test
    fun `skills filters out inactive skills`() {
        val activeSkill = createSkill("crm", "CRM operations")
        val inactiveSkill = createSkill("broken", "Broken skill", requiredBinaries = listOf("nonexistent-binary-xyz"))
        doReturn(listOf(createBootstrap("007", skills = listOf(activeSkill, inactiveSkill)))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills", List::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(1, body.size)
        assertEquals("crm", (body[0] as Map<*, *>)["name"])
    }

    @Test
    fun `skills returns empty list when no skills`() {
        doReturn(listOf(createBootstrap("007", skills = emptyList()))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body!!.size)
    }

    @Test
    fun `skills returns 404 when agent not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/skills", List::class.java)

        assertEquals(404, response.statusCode.value())
    }

    private fun createSkill(
        name: String,
        description: String = "",
        requiredBinaries: List<String> = emptyList(),
    ): Skill {
        val metadata = SkillMetadata(
            name = name,
            home = File("."),
            description = description,
            requiredBinaries = requiredBinaries,
        )
        return Skill(metadata, "")
    }

    private fun createBootstrap(name: String, skills: List<Skill> = emptyList()): Bootstrap {
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val skillRegistry = mock<SkillRegistry>()
        doReturn(skills).whenever(skillRegistry).all()

        val context = Context(
            assistant = assistant,
            home = File("target/skill-controller/$name"),
            llm = mock<LLM>(),
            skillRegistry = skillRegistry,
        )
        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
