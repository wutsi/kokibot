package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class BootstrapSetTest {
    private val contextFactory = mock<ContextFactory>()
    private val bootstrap = Bootstrap(contextFactory)
    private val context = mock<Context>()
    private val assistant = mock<Assistant>()

    private val tempDir = File("target/test-data/bootstrap-set")

    @BeforeEach
    fun setup() {
        tempDir.deleteRecursively()
        val configDir = File(tempDir, "config")
        configDir.mkdirs()
        File(configDir, "settings.json").writeText(
            """{"assistant":{"max-iterations":10},"llm":{"type":"deepseek"}}""",
        )

        doReturn(tempDir).whenever(context).home
        doReturn(assistant).whenever(context).assistant
        doReturn(Health(id = "-")).whenever(context).health()
        doReturn(context).whenever(contextFactory).create(any(), any())

        bootstrap.init(getResourceFile("/home/007"))
    }

    @Test
    fun `set updates assistant section in settings json on disk`() {
        bootstrap.set("max-iterations", 20)

        val raw = JsonMapper().readValue(File(tempDir, "config/settings.json"), Map::class.java)
        val section = raw["assistant"] as Map<*, *>
        assertEquals(20, section["max-iterations"])
    }

    @Test
    fun `set preserves other sections in settings json`() {
        bootstrap.set("description", "hello")

        val raw = JsonMapper().readValue(File(tempDir, "config/settings.json"), Map::class.java)
        val llm = raw["llm"] as Map<*, *>
        assertEquals("deepseek", llm["type"])
    }

    @Test
    fun `set calls assistant apply with key and value`() {
        bootstrap.set("description", "hello")

        verify(assistant).apply("description", "hello")
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapSetTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return File(resource.toURI())
    }
}
