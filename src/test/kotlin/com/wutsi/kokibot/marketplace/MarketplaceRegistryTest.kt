package com.wutsi.kokibot.marketplace

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.MarketplaceNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class MarketplaceRegistryTest {
    private val file = File("target/test-data/marketplace-registry")
    private val context = Context(home = file, llm = mock())
    private val registry = MarketplaceRegistry()
    private val config = mapOf(
        "marketplaces" to listOf(
            mapOf(
                "name" to "obsidian",
                "repo_url" to "https://github.com/kepano/obsidian-skills",
            ),
            mapOf(
                "name" to "anthropics",
                "repo_url" to "https://github.com/anthropics/skills",
            ),
            mapOf(
                "name" to "x1x",
                "repo_url" to "https://github.com/x1x/skills-not-found.git",
            ),
        )
    )

    @Test
    fun init() {
        registry.init(config, context)

        val marketplaces = registry.all()
        assertEquals(3, marketplaces.size)

        assertEquals("marketplace:obsidian", marketplaces[0].id())
        assertEquals("marketplace:anthropics", marketplaces[1].id())
        assertEquals("marketplace:x1x", marketplaces[2].id())
    }

    @Test
    fun `init with bad config`() {
        val config = mapOf(
            "marketplaces" to listOf(
                mapOf(
                    "name" to "obsidian",
                    "repo_url" to "https://github.com/kepano/obsidian-skills",
                ),
                mapOf(
                    "name" to "anthropics",
                    "repo_url" to "https://github.com/anthropics/skills",
                ),
                mapOf(
                    "name" to "x1X",
                ),
            )
        )

        registry.init(config, context)

        val marketplaces = registry.all()
        assertEquals(2, marketplaces.size)

        assertEquals("marketplace:obsidian", marketplaces[0].id())
        assertEquals("marketplace:anthropics", marketplaces[1].id())
    }

    @Test
    fun destroy() {
        registry.init(config, context)
        registry.destroy()

        val marketplaces = registry.all()
        assertEquals(0, marketplaces.size)
    }

    @Test
    fun `get - invalid id`() {
        registry.init(config, context)
        assertThrows<MarketplaceNotFoundException> { registry.get("invalid-id") }
    }
}
