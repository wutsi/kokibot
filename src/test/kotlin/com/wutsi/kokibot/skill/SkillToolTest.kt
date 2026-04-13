package com.wutsi.kokibot.skill

import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillToolTest {
    val context = Context(
        home = getResourceFile("/home/007"),
        llm = mock()
    )
    private val skill = Skill(
        metadata = SkillMetadata(
            name = "land-title-verifier",
        )
    )

    @Test
    fun id() {
        val metadata = ToolMetadata(
            name = "check_title",
            description = "Verify land title information",
        )
        val tool = SkillTool(skill, metadata)

        assertEquals("tool:check_title", tool.id())
    }

    @Test
    fun metadata() {
        val metadata = ToolMetadata(
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
        )
        val tool = SkillTool(skill, metadata)

        assertEquals(metadata, tool.metadata())
    }

    @Test
    fun `exec sh`() {
        val metadata = ToolMetadata(
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
        )
        val tool = SkillTool(skill, metadata)
        tool.init(emptyMap<String, Any>(), context)

        val result = tool.exec(
            mapOf(
                "title_number" to "1234/LIT",
                "region" to "Littoral",
            )
        )

        assertEquals("This land title 1234/LIT in Littoral is valid\n", result)
    }

    @Test
    fun `exec py`() {
        val metadata = ToolMetadata(
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
        val tool = SkillTool(skill, metadata)
        tool.init(emptyMap<String, Any>(), context)

        val result = tool.exec(
            mapOf(
                "title_number" to "1234/LIT",
            )
        )

        assertEquals(
            "This land title was previously owned by: John Doe from 1960 to 2020, Roger Milla from 2020 to 2024\n",
            result
        )
    }

    @Test
    fun `exec invalid script`() {
        val metadata = ToolMetadata(
            name = "xxx",
            description = "Verify land title history",
        )
        val tool = SkillTool(skill, metadata)
        tool.init(emptyMap<String, Any>(), context)

        val result = tool.exec(
            mapOf(
                "title_number" to "1234/LIT",
            )
        )

        assertEquals(
            "Sorry, I cannot execute the tool `${metadata.name}`. No script found!\n",
            result
        )
    }

    @Test
    fun `health - up`() {
        val metadata = ToolMetadata(
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
        val tool = SkillTool(skill, metadata)
        tool.init(emptyMap<String, Any>(), context)

        val health = tool.health()

        assertTrue(health.up)
        assertEquals(tool.id(), health.id)
    }

    @Test
    fun `health - down`() {
        val metadata = ToolMetadata(
            name = "no_script",
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
        val tool = SkillTool(skill, metadata)
        tool.init(emptyMap<String, Any>(), context)

        val health = tool.health()

        assertFalse(health.up)
        assertEquals(tool.id(), health.id)
        assertNotNull(health.details)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
