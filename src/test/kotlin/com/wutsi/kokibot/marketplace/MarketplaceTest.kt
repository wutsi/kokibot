package com.wutsi.kokibot.marketplace

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class MarketplaceTest {
    private val file = File("target/test-data/marketplace")
    private val context = Context(home = file, llm = mock())
    private val finder = mock<GitSkillFinder>()
    private val marketplace = Marketplace(finder)

    @BeforeEach
    fun setup() {
        if (file.exists()) {
            file.deleteRecursively()
        }
        file.mkdirs()
    }

    @Test
    fun getSkills() {
        // GIVEN
        val files = listOf(
            File(this::class.java.getResource("/home/007/skills/crm/SKILL.md")!!.file),
            File(this::class.java.getResource("/home/007/skills/land-title-verifier/SKILL.md")!!.file)
        )
        doReturn(files).whenever(finder).find(any(), any())

        // WHEN

        val config = mapOf(
            "name" to "obsidian",
            "repo-url" to "https://github.com/kepano/obsidian-skills"
        )
        marketplace.init(config, context)
        val skills = marketplace.getSkills()

        // THEN
        verify(finder).find(
            "https://github.com/kepano/obsidian-skills",
            File(context.home.absolutePath + "/workspace/marketplaces/obsidian")
        )

        assertEquals("marketplace:obsidian", marketplace.id())
        assertEquals(files.size, skills.size)
        assertNotNull(skills.find { skill -> skill.metadata.name == "obsidian/cmr" })
        assertNotNull(skills.find { skill -> skill.metadata.name == "obsidian/land-title-verifier" })
    }

    @Test
    fun `getSkills - with whitelist`() {
        // GIVEN
        val files = listOf(
            File(this::class.java.getResource("/home/007/skills/crm/SKILL.md")!!.file),
            File(this::class.java.getResource("/home/007/skills/land-title-verifier/SKILL.md")!!.file)
        )
        doReturn(files).whenever(finder).find(any(), any())

        // WHEN

        val config = mapOf(
            "name" to "obsidian",
            "repo-url" to "https://github.com/kepano/obsidian-skills",
            "skill-whitelist" to listOf("land-title-verifier")
        )
        marketplace.init(config, context)
        val skills = marketplace.getSkills()

        // THEN
        verify(finder).find(
            "https://github.com/kepano/obsidian-skills",
            File(context.home.absolutePath + "/workspace/marketplaces/obsidian")
        )

        assertEquals("marketplace:obsidian", marketplace.id())
        assertEquals(1, skills.size)
        assertNotNull(skills.find { skill -> skill.metadata.name == "obsidian/land-title-verifier" })
    }

    @Test
    fun `init - no repo-url`() {
        val config = mapOf(
            "name" to "obsidian",
        )

        assertThrows<ConfigurationException> { marketplace.init(config, context) }
    }

    @Test
    fun `init - no name`() {
        val config = mapOf(
            "repo-url" to "https://github.com/kepano/obsidian-skills"
        )

        assertThrows<ConfigurationException> { marketplace.init(config, context) }
    }
}
