package com.wutsi.kokibot.skill

import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.ConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class SkillParserTest {
    val parser = SkillParser()

    @Test
    fun parse() {
        val file = getResourceFile("/skills/land-title-verifier/SKILL.md")

        val result = parser.parse(file)

        val meta = result.first
        assertEquals("land-title-verifier", meta.name)
        assertEquals(listOf("real estate", "legal", "verification"), meta.categories)
        assertEquals(
            listOf("land title", "property verification", "Cameroon", "Titre Foncier", "fraud detection"),
            meta.keywords
        )
        assertEquals(
            "Validates land titles against the Cameroon digital registry to prevent fraud and verify ownership.",
            meta.description
        )
        assertEquals(listOf("java", "mvn"), meta.requiredBinaries)
        assertEquals(listOf("REGISTRY_API_KEY", "DB_URL"), meta.requiredEnv)
        assertEquals(listOf("echo \"hello\""), meta.requiredSetup)
        assertEquals(file.parentFile, meta.home)
        assertEquals(listOf("linux", "windows"), meta.requiredOS)

        assertEquals(
            """
# Skill: Land Title Verifier

This skill allows the agent to check the authenticity of a "Titre Foncier" (Land Title) in Cameroon. It should be used
whenever a user provides a title number or asks for property verification.

## Tools

- `check_title`: Queries the digital land registry database.
    - `title_number`: (string) The unique ID of the land title (e.g., "1234/LIT").
    - `region`: (string) Optional. The administrative region, such as "Littoral", "Centre", or "Ouest".

- `get_title_history`: Return the chain of previous owners for a given title number.
    - `title_number`: (invalid_type) The unique ID of the land title (e.g., "1234/LIT").

## Instructions

- **Verification Protocol**: If a title is found, always report the "Nom du Propriétaire" (Owner Name) and the "
  Superficie" (Area in square meters).
- **Fraud Detection**: If the title number does not match the expected regional format (e.g., missing the region
  suffix), flag it as "Suspicious" and suggest a physical "Concierge Verification."
- **Tone**: Maintain a professional, legalistic tone. Use French terminology for official document names (e.g., "
  Certificat de Propriété") but keep the summary in the user's preferred language.

## Examples

**User:** "Can you check land title 5678/LIT in Douala?"
**Action:** Call `check_title(title_number="5678/LIT", region="Littoral")`

**User:** "I want to see the history of this property: 9912/CEN."
**Action:** Call `get_title_history(title_number="9912/CEN")`
            """.trimIndent(),
            result.second,
        )
    }

    @Test
    fun minimal() {
        val file = getResourceFile("/skills/minimal/SKILL.md")

        val result = parser.parse(file)

        val meta = result.first
        assertEquals("minimal", meta.name)
        assertEquals(emptyList<String>(), meta.categories)
        assertEquals(emptyList<String>(), meta.keywords)
        assertEquals("", meta.description)
        assertEquals(emptyList<String>(), meta.requiredBinaries)
        assertEquals(emptyList<String>(), meta.requiredEnv)
        assertEquals(emptyList<String>(), meta.requiredSetup)
        assertEquals(file.parentFile, meta.home)
        assertEquals(emptyList<String>(), meta.requiredOS)

        assertEquals(
            """
Yo
            """.trimIndent(),
            result.second,
        )
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
