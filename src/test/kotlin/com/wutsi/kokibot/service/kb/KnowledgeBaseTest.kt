package com.wutsi.kokibot.service.kb

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.config.JacksonConfiguration
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnowledgeBaseTest {
    private val kb = KnowledgeBase()
    private val context = Context(
        home = File("target/test-data/kb"),
        llm = mock(),
        jsonMapper = JacksonConfiguration().jsonMapper(),
    )
    private val config = mapOf("enabled" to true, "exclusive" to true)

    @BeforeEach
    fun setUp() {
        context.home.deleteRecursively()
        context.fileService.init(config, context)
    }

    @Test
    fun id() {
        assertEquals(KnowledgeBase.ID, kb.id())
    }

    @Test
    fun `init - defaults`() {
        kb.init(emptyMap<String, Any>(), context)
        assertTrue(kb.isEnabled())
        assertTrue(kb.isExclusive())
    }

    @Test
    fun `apply - enabled`() {
        kb.init(config, context)
        kb.apply("enabled", false)
        assertFalse(kb.isEnabled())
    }

    @Test
    fun `apply - exclusive`() {
        kb.init(config, context)
        kb.apply("exclusive", false)
        assertFalse(kb.isExclusive())
    }

    @Test
    fun `apply - unknown key`() {
        kb.init(config, context)
        assertThrows<ConfigurationException> { kb.apply("unknown", "value") }
    }

    @Test
    fun ingest() {
        // GIVEN
        kb.init(config, context)

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = """
                        {
                            "summary": "This is a sample document.",
                            "keywords": ["sample", "document"],
                            "scope": "This is the scope of the document"
                        }
                    """.trimIndent()
                )
            )
        )
        doReturn(llmResponse).whenever(context.llm).completion(any(), any())

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)

        // THEN
        val entries = kb.readIndex()
        assertEquals(1, entries.size)
        assertEquals(file.name, entries[0].name)
        assertEquals("This is the scope of the document", entries[0].scope)
        assertEquals(listOf("sample", "document"), entries[0].keywords)
        assertEquals(File(context.home, "kb/raw/${file.name}.md").absolutePath, entries[0].raw)
        assertEquals(File(context.home, "kb/raw/${file.name}.summary.md").absolutePath, entries[0].summary)
        assertEquals(File(context.home, "kb/source/${file.name}").absolutePath, entries[0].source)

        assertTrue(File(context.home, "kb/source/${file.name}").exists())

        assertTrue(File(context.home, "kb/raw/${file.name}.md").exists())

        assertTrue(File(context.home, "kb/raw/${file.name}.summary.md").exists())
        assertEquals("This is a sample document.", File(context.home, "kb/raw/${file.name}.summary.md").readText())
    }

    @Test
    fun `ingest - already exists`() {
        // GIVEN
        kb.init(config, context)

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = """
                        {
                            "summary": "This is a sample document.",
                            "keywords": ["sample", "document"],
                            "scope": "This is the topic of the document."
                        }
                    """.trimIndent()
                )
            )
        )
        doReturn(llmResponse).whenever(context.llm).completion(any(), any())

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        assertThrows<FileAlreadyIngestedException> { kb.ingest(file) }
    }
}
