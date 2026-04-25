package com.wutsi.kokibot.marketplace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class GitSkillFinderTest {
    private val file = File("target/test-data/git-skill-finder")
    val finder = GitSkillFinder()

    @BeforeEach
    fun setup() {
        if (file.exists()) {
            file.deleteRecursively()
        }
        file.mkdirs()
    }

    @Test
    fun `clone and find`() {
        // WHEN
        val skillFiles = finder.find("https://github.com/kepano/obsidian-skills", file)

        // THEN
        assertEquals(5, skillFiles.size)
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/defuddle").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/json-canvas").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-bases").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-cli").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-markdown").exists())
    }

    @Test
    fun `update and find`() {
        // WHEN
        finder.find("https://github.com/kepano/obsidian-skills.git", file)
        val skillFiles = finder.find("https://github.com/kepano/obsidian-skills", file)

        // THEN
        assertEquals(5, skillFiles.size)
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/defuddle").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/json-canvas").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-bases").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-cli").exists())
        assertTrue(File(file.absolutePath + "/obsidian-skills/skills/obsidian-markdown").exists())
    }
}
