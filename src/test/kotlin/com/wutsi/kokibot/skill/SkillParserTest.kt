package com.wutsi.kokibot.skill

import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals

class SkillParserTest {
    val parser = SkillParser()

    @Test
    fun parse() {
        val file = getResourceFile("/skills/land-title-verifier/SKILL.md")

        val meta = parser.parse(file)

        assertEquals("land-title-verifier", meta.name)
        assertEquals(
            listOf("real estate", "legal", "verification"),
            meta.categories
        )
        assertEquals(
            listOf("land title", "property verification", "Cameroon", "Titre Foncier", "fraud detection"),
            meta.keywords
        )
        assertEquals(
            "Validates land titles against the Cameroon digital registry to prevent fraud and verify ownership.",
            meta.description
        )
        assertEquals(
            listOf("java", "mvn"),
            meta.requiredBins
        )
        assertEquals(
            listOf("REGISTRY_API_KEY", "DB_URL"),
            meta.requiredEnv
        )

        assertEquals(2, meta.tools.size)

        assertEquals("check_title", meta.tools[0].name)
        assertEquals(2, meta.tools[0].parameters.size)
        assertEquals("title_number", meta.tools[0].parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.tools[0].parameters[0].type)
        assertEquals(true, meta.tools[0].parameters[0].required)
        assertEquals("region", meta.tools[0].parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.tools[1].parameters[0].type)
        assertEquals(false, meta.tools[0].parameters[1].required)

        assertEquals("get_title_history", meta.tools[1].name)
        assertEquals(1, meta.tools[1].parameters.size)
        assertEquals("title_number", meta.tools[1].parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.tools[1].parameters[0].type)
        assertEquals(true, meta.tools[1].parameters[0].required)
    }

    @Test
    fun `parse - no header`() {
        val file = getResourceFile("/skills/no-header/SKILL.md")

        assertThrows<ConfigurationException> { parser.parse(file) }
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
