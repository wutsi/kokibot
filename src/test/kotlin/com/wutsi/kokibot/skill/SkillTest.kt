package com.wutsi.kokibot.skill

import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class SkillTest {
    val context = Context(
        home = getResourceFile("/home/007"),
        llm = mock()
    )
    private val skill = Skill(
        metadata = SkillMetadata(
            name = "land-title-verifier",
            tools = listOf(
                ToolMetadata(
                    name = "check_title",
                    description = "Verify land title information",
                    parameters = listOf(
                        ToolParameter(
                            name = "title_number",
                            description = "The unique ID of the land title (e.g., \"1234/LIT\")",
                            required = true,
                            type = ToolParameterType.STRING,
                        ),
                        ToolParameter(
                            name = "region",
                            description = "The administrative region, such as \"Littoral\", \"Centre\", or \"Ouest\".",
                            required = true,
                            type = ToolParameterType.STRING,
                        ),
                    )
                ),
                ToolMetadata(
                    name = "get_title_history",
                    description = "Verify land title history",
                    parameters = listOf(
                        ToolParameter(
                            name = "title_number",
                            description = "The unique ID of the land title (e.g., \"1234/LIT\")",
                            required = true,
                            type = ToolParameterType.STRING,
                        ),
                    )
                )
            ),
            requiredBins = listOf("java"),
            requiredEnv = listOf("PATH"),
        )
    )

    @Test
    fun id() {
        assertEquals("skill:land-title-verifier", skill.id())
    }

    @Test
    fun init() {
        skill.init(emptyMap<String, Any>(), context)

        val tools = skill.getTools()
        assertEquals(2, tools.size)

        assertEquals(skill.metadata.tools[0], tools[0].metadata())
        assertEquals(skill.metadata.tools[1], tools[1].metadata())
    }

    @Test
    fun destroy() {
        skill.init(emptyMap<String, Any>(), context)
        skill.destroy()
    }

    @Test
    fun health() {
        skill.init(emptyMap<String, Any>(), context)
        val health = skill.health()

        assertEquals(skill.id(), health.id)
        assertEquals(2, health.children.size)
        assertEquals(true, health.up)
        assertNull(health.details)
    }

    @Test
    fun `health - missing env`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(requiredEnv = listOf("__MISSING_ENV__"))
        )
        xskill.init(emptyMap<String, Any>(), context)
        val health = xskill.health()

        assertEquals(false, health.up)
        assertNotNull(health.details)
    }

    @Test
    fun `health - missing bin`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(requiredBins = listOf("__missin_bin__"))
        )
        xskill.init(emptyMap<String, Any>(), context)
        val health = xskill.health()

        assertEquals(false, health.up)
        assertNotNull(health.details)
    }

    @Test
    fun `health - missing tool script`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(
                tools = listOf(
                    ToolMetadata(
                        name = "missing_script",
                        description = "Verify land title information",
                    )
                )
            )
        )
        xskill.init(emptyMap<String, Any>(), context)
        val health = xskill.health()

        assertEquals(false, health.up)
        assertNotNull(health.details)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
