package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class MarketplaceRegistryTest {
    private val home = File("target/test-data/marketplace-registry")
    private val context = Context(home = home, llm = mock())
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
}
