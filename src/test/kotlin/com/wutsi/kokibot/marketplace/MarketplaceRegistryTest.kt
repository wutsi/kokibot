package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.skill.SkillRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class MarketplaceRegistryTest {
    private val home = File("target/test-data/marketplace-registry")
    private val skillRegistry = mock<SkillRegistry>()
    private val context = Context(home = home, llm = mock(), skillRegistry = skillRegistry)
    private val registry = MarketplaceRegistry()

    @BeforeEach
    fun setup() {
        if (home.exists()) {
            home.deleteRecursively()
        }
        val dir = File(home, "config/marketplaces")
        dir.mkdirs()

        File(dir, "obsidian.json").writeText(
            """{ "name": "obsidian", "repo-url": "https://github.com/kepano/obsidian-skills" }"""
        )
        File(dir, "anthropics.json").writeText(
            """{ "name": "anthropics", "repo-url": "https://github.com/anthropics/skills" }"""
        )
        File(dir, "x1x.json").writeText(
            """{ "name": "x1x", "repo-url": "https://github.com/x1x/skills-not-found.git" }"""
        )
    }

    @Test
    fun init() {
        registry.init(context)

        val marketplaces = registry.all()
        assertEquals(3, marketplaces.size)

        assertEquals("marketplace:anthropics", marketplaces[0].id())
        assertEquals("marketplace:obsidian", marketplaces[1].id())
        assertEquals("marketplace:x1x", marketplaces[2].id())
    }

    @Test
    fun `init - bad config`() {
        File(home, "config/marketplaces/bad.json").writeText(
            """{ "name": "bad-marketplace" }"""
        )

        registry.init(context)

        val marketplaces = registry.all()
        assertEquals(3, marketplaces.size)

        assertEquals("marketplace:anthropics", marketplaces[0].id())
        assertEquals("marketplace:obsidian", marketplaces[1].id())
        assertEquals("marketplace:x1x", marketplaces[2].id())
    }

    @Test
    fun `init - no marketplaces directory`() {
        File(home, "config/marketplaces").deleteRecursively()

        registry.init(context)

        val marketplaces = registry.all()
        assertEquals(0, marketplaces.size)
    }

    @Test
    fun `init - loads global marketplaces`() {
        val globalDir = File(home.parentFile.parentFile, "config/marketplaces")
        globalDir.mkdirs()
        File(globalDir, "global-mp.json").writeText(
            """{ "name": "global-mp", "repo-url": "https://github.com/global/skills" }"""
        )

        try {
            registry.init(context)

            val marketplaces = registry.all()
            assertEquals(4, marketplaces.size)
            assertEquals("marketplace:anthropics", marketplaces[0].id())
            assertEquals("marketplace:global-mp", marketplaces[1].id())
            assertEquals("marketplace:obsidian", marketplaces[2].id())
            assertEquals("marketplace:x1x", marketplaces[3].id())
        } finally {
            globalDir.deleteRecursively()
        }
    }

    @Test
    fun `init - agent marketplace overrides global marketplace with same name`() {
        val globalDir = File(home.parentFile.parentFile, "config/marketplaces")
        globalDir.mkdirs()
        File(globalDir, "obsidian.json").writeText(
            """{ "name": "obsidian", "repo-url": "https://github.com/global/obsidian-skills" }"""
        )

        try {
            registry.init(context)

            val marketplaces = registry.all()
            assertEquals(3, marketplaces.size)
            val obsidian = registry.get("marketplace:obsidian")
            assertEquals("https://github.com/kepano/obsidian-skills", obsidian.getRepoUrl())
        } finally {
            globalDir.deleteRecursively()
        }
    }

    @Test
    fun destroy() {
        registry.init(context)
        registry.destroy()

        val marketplaces = registry.all()
        assertEquals(0, marketplaces.size)
    }

    @Test
    fun `get - invalid id`() {
        registry.init(context)
        assertThrows<MarketplaceNotFoundException> { registry.get("invalid-id") }
    }

    @Test
    fun `apply - invalid id`() {
        registry.init(context)
        assertThrows<MarketplaceNotFoundException> { registry.apply("invalid-id.enabled", true) }
    }

    @Test
    fun `apply - invalid property`() {
        registry.init(context)
        assertThrows<IllegalArgumentException> { registry.apply("obsidian", true) }
    }

    @Test
    fun `apply - enable removes from disabled set and registers skills`() {
        registry.init(context)
        registry.apply("obsidian.enabled", false)

        registry.apply("obsidian.enabled", true)

        val marketplace = registry.get("marketplace:obsidian")
        assertTrue(registry.isEnabled(marketplace))
    }

    @Test
    fun `apply - disable adds to disabled set and unregisters skills`() {
        registry.init(context)

        registry.apply("obsidian.enabled", false)

        val marketplace = registry.get("marketplace:obsidian")
        assertFalse(registry.isEnabled(marketplace))
    }

    @Test
    fun `init - loads disabled marketplaces from config`() {
        val contextWithDisabled = Context(
            home = home,
            llm = mock(),
            skillRegistry = skillRegistry,
            config = mapOf("marketplaces" to mapOf("disabled" to listOf("obsidian"))),
        )

        registry.init(contextWithDisabled)

        val obsidian = registry.get("marketplace:obsidian")
        assertFalse(registry.isEnabled(obsidian))
        assertTrue(registry.isEnabled(registry.get("marketplace:anthropics")))
    }
}
