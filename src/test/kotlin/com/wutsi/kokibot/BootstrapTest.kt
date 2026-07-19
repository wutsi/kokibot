package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFactory
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import com.wutsi.kokibot.service.heartbeat.Heartbeat
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.SkillRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File

class BootstrapTest {
    private val contextFactory = mock<ContextFactory>()
    private val bootstrap = Bootstrap(contextFactory)

    private val home = File("target/test-data/bootstrap")
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()
    private val heartbeat = mock<Heartbeat>()
    private val memory = mock<Memory>()
    private val marketplaceRegistry = mock<MarketplaceRegistry>()
    private val skillRegistry = mock<SkillRegistry>()
    private val llm = mock<LLM>()
    private val jsonMapper = JsonMapper()

    @BeforeEach
    fun setup() {
        doReturn(jsonMapper).whenever(context).jsonMapper
        doReturn(home).whenever(context).home
        doReturn(assistant).whenever(context).assistant
        doReturn(memory).whenever(context).memory
        doReturn(marketplaceRegistry).whenever(context).marketplaceRegistry
        doReturn(Health(id = "-")).whenever(context).health()
        doReturn(context).whenever(contextFactory).create(any(), any(), any())
        doReturn(llm).whenever(context).llm
        doReturn(heartbeat).whenever(context).heartbeat
        doReturn(skillRegistry).whenever(context).skillRegistry
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
        val saved = jsonMapper.readValue(
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
        val saved = jsonMapper.readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals(false, (saved["memory"] as Map<*, *>)["enabled"])
    }

    @Test
    fun `set - llm property`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.set("llm.temperature", 0.7)

        verify(llm).apply("temperature", 0.7)
        val saved = jsonMapper.readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals(0.7, (saved["llm"] as Map<*, *>)["temperature"])
    }

    @Test
    fun `set - marketplace enabled persists disabled list`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        doReturn(setOf("foo")).whenever(marketplaceRegistry).disabledMarketplaces()

        bootstrap.set("marketplace.foo.enabled", false)

        verify(marketplaceRegistry).apply("foo.enabled", false)
        val saved = jsonMapper.readValue(File(home, "config/settings.json"), Map::class.java)
        assertEquals(listOf("foo"), (saved["marketplaces"] as Map<*, *>)["disabled"])
    }

    @Test
    fun `set - marketplace non-enabled property does not update settings json`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("marketplace.foo.description", "new description")

        verify(marketplaceRegistry).apply("foo.description", "new description")
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun `set - skill disabled persists disabled list`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        doReturn(setOf("foo")).whenever(skillRegistry).disabledSkills()

        bootstrap.set("skill.foo.enabled", false)

        verify(skillRegistry).apply("foo.enabled", false)
        val saved = jsonMapper.readValue(File(home, "config/settings.json"), Map::class.java)
        assertEquals(listOf("foo"), (saved["skills"] as Map<*, *>)["disabled"])
    }

    @Test
    fun `set - skill instructions does not update settings json`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("skill.foo.instructions", "new instructions")

        verify(skillRegistry).apply("foo.instructions", "new instructions")
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun `set - assistant instructions skips settings json write`() {
        setupSettingsFile()
        bootstrap.init(home)
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("assistant.instructions", "You are a helpful assistant")

        verify(assistant).apply("instructions", "You are a helpful assistant")
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun `set - heartbeat instructions skips settings json write`() {
        setupSettingsFile()
        bootstrap.init(home)
        val before = File(home, "config/settings.json").readText()

        bootstrap.set("heartbeat.instructions", "You are a helpful assistant")

        verify(heartbeat).apply("instructions", "You are a helpful assistant")
        assertEquals(before, File(home, "config/settings.json").readText())
    }

    @Test
    fun changeLLM() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        val newLlm = mock<LLM>()
        doReturn(listOf("gpt-4", "gpt-3.5")).whenever(newLlm).availableModels()
        val llmFactory = mock<LLMFactory>()
        doReturn(newLlm).whenever(llmFactory).create("openai")
        doReturn(llmFactory).whenever(contextFactory).llmFactory

        bootstrap.changeLLM("openai", "gpt-4")

        verify(llmFactory).create("openai")
        verify(context).destroy()
        val saved = jsonMapper.readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals("openai", (saved["llm"] as Map<*, *>)["name"])
        assertEquals("gpt-4", (saved["llm"] as Map<*, *>)["model"])
    }

    @Test
    fun `changeLLM - invalid model throws`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        val newLlm = mock<LLM>()
        doReturn(listOf("gpt-4", "gpt-3.5")).whenever(newLlm).availableModels()
        val llmFactory = mock<LLMFactory>()
        doReturn(newLlm).whenever(llmFactory).create("openai")
        doReturn(llmFactory).whenever(contextFactory).llmFactory

        assertThrows<ConfigurationException> { bootstrap.changeLLM("openai", "invalid-model") }
    }

    @Test
    fun `changeLLM - preserves existing settings`() {
        val configDir = File(home, "config")
        configDir.mkdirs()
        File(configDir, "settings.json").writeText("""{"assistant":{"max-iterations":10},"memory":{"enabled":true}}""")
        bootstrap.init(getResourceFile("/home/007"))

        val newLlm = mock<LLM>()
        doReturn(listOf("gpt-4")).whenever(newLlm).availableModels()
        val llmFactory = mock<LLMFactory>()
        doReturn(newLlm).whenever(llmFactory).create("openai")
        doReturn(llmFactory).whenever(contextFactory).llmFactory

        bootstrap.changeLLM("openai", "gpt-4")

        val saved = jsonMapper.readValue(
            File(home, "config/settings.json"),
            Map::class.java,
        )
        assertEquals(10, (saved["assistant"] as Map<*, *>)["max-iterations"])
        assertEquals(true, (saved["memory"] as Map<*, *>)["enabled"])
        assertEquals("openai", (saved["llm"] as Map<*, *>)["name"])
        assertEquals("gpt-4", (saved["llm"] as Map<*, *>)["model"])
    }

    @Test
    fun `changeLLM - empty available models throws`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        val newLlm = mock<LLM>()
        doReturn(emptyList<String>()).whenever(newLlm).availableModels()
        val llmFactory = mock<LLMFactory>()
        doReturn(newLlm).whenever(llmFactory).create("openai")
        doReturn(llmFactory).whenever(contextFactory).llmFactory

        assertThrows<ConfigurationException> { bootstrap.changeLLM("openai", "gpt-4") }
    }

    @Test
    fun `set - unknown section throws`() {
        setupSettingsFile()
        bootstrap.init(getResourceFile("/home/007"))

        assertThrows<ConfigurationException> { bootstrap.set("foo.model", "x") }
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
