package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import com.wutsi.kokibot.service.memory.Memory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class BootstrapTest {
    private val contextFactory = mock<ContextFactory>()
    private val bootstrap = Bootstrap(contextFactory)

    private val home = File("target/test-data/bootstrap")
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()
    private val memory = mock<Memory>()
    private val marketplaceRegistry = mock<MarketplaceRegistry>()

    @BeforeEach
    fun setup() {
        doReturn(home).whenever(context).home
        doReturn(assistant).whenever(context).assistant
        doReturn(memory).whenever(context).memory
        doReturn(marketplaceRegistry).whenever(context).marketplaceRegistry
        doReturn(Health(id = "-")).whenever(context).health()
        doReturn(context).whenever(contextFactory).create(any(), any())
    }

    @Test
    fun destroy() {
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.destroy()

        verify(context).destroy()
    }

    @Test
    fun init() {
        val home = getResourceFile("/home/007")
        bootstrap.init(home)

        verify(context).init(any())
    }

    @Test
    fun `set - assistant property`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.set("assistant.max-iterations", 5)

        verify(assistant).apply("max-iterations", 5)
        val saved = tools.jackson.databind.json.JsonMapper().readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals(5, (saved["assistant"] as Map<*, *>)["max-iterations"])
    }

    @Test
    fun `set - memory property`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.set("memory.enabled", false)

        verify(memory).apply("enabled", false)
        val saved = tools.jackson.databind.json.JsonMapper().readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals(false, (saved["memory"] as Map<*, *>)["enabled"])
    }

    @Test
    fun `set - marketplace property`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("marketplace.foo.enabled", false)

        verify(marketplaceRegistry).apply("foo.enabled", false)
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun `set - assistant instructions skips settings json write`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("assistant.instructions", "You are a helpful assistant")

        verify(assistant).apply("instructions", "You are a helpful assistant")
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun `set - unknown section throws`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        assertThrows<ConfigurationException> { bootstrap.set("llm.model", "x") }
    }

    @Test
    fun `set - missing dot throws`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        assertThrows<ConfigurationException> { bootstrap.set("max-iterations", 5) }
    }

    private fun setupSettingsFile() {
        val configDir = File(home, "config")
        configDir.mkdirs()
        File(configDir, "settings.json").writeText("""{"assistant":{},"memory":{}}""")
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
