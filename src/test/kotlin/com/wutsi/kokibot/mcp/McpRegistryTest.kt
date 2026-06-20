package com.wutsi.kokibot.mcp

import com.wutsi.kokibot.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class McpRegistryTest {
    private val home = File("target/test-data/mcp-registry")
    private val context = Context(home = home, llm = mock())
    private val registry = McpRegistry()

    @BeforeEach
    fun setUp() {
        if (home.exists()) home.deleteRecursively()
        val dir = File(home, "config/mcps")
        dir.mkdirs()

        File(dir, "weather.json").writeText(
            """{"name":"weather-mcp","description":"Weather data","url":"https://weather.example.com/mcp"}"""
        )
        File(dir, "news.json").writeText(
            """{"name":"news-mcp","description":"News feeds","url":"https://news.example.com/mcp","token":"tok-abc"}"""
        )
    }

    @Test
    fun `init discovers all JSON configs`() {
        registry.init(emptyMap<String, Any>(), context)

        val servers = registry.all()
        assertEquals(2, servers.size)
        assertEquals("news-mcp", servers[0].config.name)
        assertEquals("weather-mcp", servers[1].config.name)
    }

    @Test
    fun `init - no mcps directory tolerates missing dir`() {
        File(home, "config/mcps").deleteRecursively()

        registry.init(emptyMap<String, Any>(), context)

        assertEquals(0, registry.all().size)
    }

    @Test
    fun `init - skips invalid JSON file and logs warning`() {
        File(home, "config/mcps").mkdirs()
        File(home, "config/mcps/bad.json").writeText("""{"description":"missing name and url"}""")

        registry.init(emptyMap<String, Any>(), context)

        assertEquals(2, registry.all().size) // bad.json skipped
    }

    @Test
    fun `init - applies env var substitution`() {
        File(home, "config/mcps/env-test.json").writeText(
            """{"name":"env-mcp","description":"Test","url":"https://env.example.com","token":"${'$'}{MCP_REGISTRY_TEST_TOKEN}"}"""
        )

        registry.init(emptyMap<String, Any>(), context)

        val server = registry.get("env-mcp")
        // Token stays as placeholder when env var not set
        assertEquals("\${MCP_REGISTRY_TEST_TOKEN}", server.config.token)
    }

    @Test
    fun `get returns server by name (case-insensitive)`() {
        registry.init(emptyMap<String, Any>(), context)

        val server = registry.get("Weather-MCP")
        assertEquals("weather-mcp", server.config.name)
    }

    @Test
    fun `get throws McpNotFoundException for unknown server`() {
        registry.init(emptyMap<String, Any>(), context)
        assertThrows<McpNotFoundException> { registry.get("unknown") }
    }

    @Test
    fun `destroy clears all servers`() {
        registry.init(emptyMap<String, Any>(), context)
        registry.destroy()
        assertEquals(0, registry.all().size)
    }
}
