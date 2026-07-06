package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillNotFoundException
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

    @Test
    fun get() {
        val meta = SkillMetadata(
            name = "crm",
            description = "CRM operations",
            keywords = listOf("crm", "customer"),
            requiredBinaries = listOf("java"),
            requiredEnv = listOf("PATH"),
            requiredOS = listOf("Mac OS X"),
            home = File("target/skill-controller/007/config/skills/crm"),
        )
        val skill = mock<Skill>()
        doReturn(meta).whenever(skill).metadata
        doReturn("# CRM Skill\nManage customers").whenever(skill).instructions
        doReturn(null).whenever(skill).marketplace

        val skillRegistry = mock<SkillRegistry>()
        doReturn(skill).whenever(skillRegistry).get("crm")
        doReturn(listOf(createBootstrapWithRegistry("007", skillRegistry))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills/crm", Map::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals("crm", body["name"])
        assertEquals("CRM operations", body["description"])
        assertEquals("# CRM Skill\nManage customers", body["instructions"])
        assertEquals(null, body["marketplace"])
    }

    @Test
    fun `get - marketplace skill`() {
        val meta = SkillMetadata(
            name = "acme_crm",
            description = "CRM operations",
            home = File("target/skill-controller/007/config/skills/acme_crm"),
        )
        val skill = mock<Skill>()
        doReturn(meta).whenever(skill).metadata
        doReturn("").whenever(skill).instructions
        doReturn("acme").whenever(skill).marketplace

        val skillRegistry = mock<SkillRegistry>()
        doReturn(skill).whenever(skillRegistry).get("acme_crm")
        doReturn(listOf(createBootstrapWithRegistry("007", skillRegistry))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills/acme_crm", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("acme", response.body!!["marketplace"])
    }

    @Test
    fun `get - skill not found`() {
        val skillRegistry = mock<SkillRegistry>()
        doThrow(SkillNotFoundException("Skill not found: xxx")).whenever(skillRegistry).get("xxx")
        doReturn(listOf(createBootstrapWithRegistry("007", skillRegistry))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/skills/xxx", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get - assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/skills/crm", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun settings() {
        val bootstrap = createBootstrapWithRegistry("007", mock())
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/skills/crm/settings",
            mapOf("key" to "instructions", "value" to "new instructions"),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("skill.crm.instructions", "new instructions")
    }

    @Test
    fun `settings - assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/xxx/skills/crm/settings",
            mapOf("key" to "instructions", "value" to "new instructions"),
            Map::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `settings - missing key`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/skills/crm/settings",
            mapOf("value" to "new instructions"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `settings - missing value`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/skills/crm/settings",
            mapOf("key" to "instructions"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `settings - configuration error`() {
        val bootstrap = createBootstrapWithRegistry("007", mock())
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(ConfigurationException("Unknown assistant setting: invalid")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/skills/crm/settings",
            mapOf("key" to "invalid", "value" to "x"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("Unknown assistant setting: invalid", (response.body as Map<*, *>)["error"])
    }

    @Test
    fun `settings - skill not found`() {
        val bootstrap = createBootstrapWithRegistry("007", mock())
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(SkillNotFoundException("Skill not found: xxx")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/skills/xxx/settings",
            mapOf("key" to "instructions", "value" to "new instructions"),
            Map::class.java,
        )

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
        return Skill(metadata)
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

    private fun createBootstrapWithRegistry(name: String, skillRegistry: SkillRegistry): Bootstrap {
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

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
