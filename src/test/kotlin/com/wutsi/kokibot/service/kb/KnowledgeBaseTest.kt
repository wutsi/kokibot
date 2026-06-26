package com.wutsi.kokibot.service.kb

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.config.JacksonConfiguration
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import java.net.URL
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
    }

    @Test
    fun id() {
        assertEquals(KnowledgeBase.ID, kb.id())
    }

    @Test
    fun `init - defaults`() {
        kb.init(emptyMap<String, Any>(), context)
        assertFalse(kb.isEnabled())
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
    fun `ingest file`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(file.name, entries[0].name)
        assertEquals(KBEntryType.FILE, entries[0].type)
        assertEquals(KBEntryStatus.PROCESSING, entries[0].status)
        assertNull(entries[0].scope)
        assertTrue(entries[0].keywords.isEmpty())
        assertEquals("kb/source/${file.name}", entries[0].source)
        assertNull(entries[0].raw)
        assertNull(entries[0].summary)
        assertNull(entries[0].error)

        assertTrue(File(context.home, entries[0].source).exists())
    }

    @Test
    fun `ingest file and process`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        Thread.sleep(5000) // Wait for the async processing to complete

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(file.name, entries[0].name)
        assertEquals(KBEntryType.FILE, entries[0].type)
        assertEquals(KBEntryStatus.READY, entries[0].status)
        assertEquals("This is the scope of the document", entries[0].scope)
        assertEquals(listOf("sample", "document"), entries[0].keywords)
        assertEquals("kb/source/${file.name}", entries[0].source)
        assertEquals("kb/data/${file.name}.md", entries[0].raw)
        assertEquals("kb/data/${file.name}.summary.md", entries[0].summary)
        assertNull(entries[0].url)
        assertNull(entries[0].error)

        assertTrue(File(context.home, entries[0].source).exists())
        assertTrue(File(context.home, entries[0].raw!!).exists())
        assertTrue(File(context.home, entries[0].summary!!).exists())
        assertEquals("This is a sample document.", File(context.home, entries[0].summary).readText())
    }

    @Test
    fun `ingest file - LLM response in json block`() {
        // GIVEN
        kb.init(config, context)

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = """
                        ```json
                        {
                            "summary": "This is a sample document.",
                            "keywords": ["sample", "document"],
                            "scope": "This is the scope of the document"
                        }
                        ```
                    """.trimIndent()
                )
            )
        )
        doReturn(llmResponse).whenever(context.llm).completion(any(), any())

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        Thread.sleep(5000) // Wait for the async processing to complete

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(file.name, entries[0].name)
        assertEquals(KBEntryType.FILE, entries[0].type)
        assertEquals(KBEntryStatus.READY, entries[0].status)
        assertEquals("This is the scope of the document", entries[0].scope)
        assertEquals(listOf("sample", "document"), entries[0].keywords)
        assertEquals("kb/data/${file.name}.md", entries[0].raw)
        assertEquals("kb/data/${file.name}.summary.md", entries[0].summary)
        assertEquals("kb/source/${file.name}", entries[0].source)
        assertNull(entries[0].url)
        assertNull(entries[0].error)

        assertTrue(File(context.home, entries[0].source).exists())
        assertTrue(File(context.home, entries[0].raw!!).exists())
        assertTrue(File(context.home, entries[0].summary!!).exists())
        assertEquals("This is a sample document.", File(context.home, entries[0].summary).readText())
    }

    @Test
    fun `ingest file - LLM error`() {
        // GIVEN
        kb.init(config, context)

        doThrow(RuntimeException("LLM error")).whenever(context.llm).completion(any(), any())

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        Thread.sleep(5000) // Wait for the async processing to complete

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(file.name, entries[0].name)
        assertEquals(KBEntryType.FILE, entries[0].type)
        assertEquals(KBEntryStatus.ERROR, entries[0].status)
        assertNull(entries[0].scope)
        assertTrue(entries[0].keywords.isEmpty())
        assertNull(entries[0].raw)
        assertNull(entries[0].raw)
        assertNull(entries[0].summary)
        assertEquals("LLM error", entries[0].error)
    }

    @Test
    fun `ingest file - already exists`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        assertThrows<FileAlreadyIngestedException> { kb.ingest(file) }
    }

    @Test
    fun `ingest file and delete and process`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val file = File(this::class.java.getResource("/file/sample.docx")!!.file)
        kb.ingest(file)
        kb.delete(file.name)
        Thread.sleep(5000) // Wait for the async processing to complete

        // THEN
        val entries = kb.entries()
        assertEquals(0, entries.size)
    }

    @Test
    fun `delete file`() {
        // GIVEN
        kb.init(config, context)

        val file = File(this::class.java.getResource("/file/sample.html")!!.file)
        kb.ingest(file)
        Thread.sleep(5000) // Wait for the async processing to complete

        // WHEN
        kb.delete("sample.html")

        // THEN
        val entries = kb.entries()
        assertEquals(0, entries.size)

        assertFalse(File(context.home, "kb/source/${file.name}").exists())
        assertFalse(File(context.home, "kb/data/${file.name}.md").exists())
        assertFalse(File(context.home, "kb/data/${file.name}.summary.md").exists())
    }

    @Test
    fun `ingest URL`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val url = URL("https://pdfobject.com/pdf/sample.pdf")
        kb.ingest(url)

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(url.toString(), entries[0].name)
        assertEquals(KBEntryType.LINK, entries[0].type)
        assertEquals(KBEntryStatus.PROCESSING, entries[0].status)
        assertNull(entries[0].scope)
        assertTrue(entries[0].keywords.isEmpty())
        assertEquals(url.toString(), entries[0].url)
        assertNull(entries[0].source)
        assertNull(entries[0].raw)
        assertNull(entries[0].summary)
        assertNull(entries[0].error)
    }

    @Test
    fun `ingest URL and process`() {
        // GIVEN
        kb.init(config, context)

        // WHEN
        val url = URL("https://pdfobject.com/pdf/sample.pdf")
        kb.ingest(url)
        Thread.sleep(5000) // Wait for the async processing to complete

        // THEN
        val entries = kb.entries()
        assertEquals(1, entries.size)
        assertEquals(url.toString(), entries[0].name)
        assertEquals(KBEntryType.LINK, entries[0].type)
        assertEquals(KBEntryStatus.READY, entries[0].status)
        assertEquals("This is the scope of the document", entries[0].scope)
        assertEquals(listOf("sample", "document"), entries[0].keywords)
        assertEquals(url.toString(), entries[0].url)
        assertEquals(true, entries[0].source?.startsWith("kb/source/"))
        assertEquals(true, entries[0].source?.endsWith(".pdf"))
        assertEquals(true, entries[0].raw?.startsWith("kb/data/"))
        assertEquals(true, entries[0].raw?.endsWith(".md"))
        assertEquals(true, entries[0].summary?.startsWith("kb/data/"))
        assertEquals(true, entries[0].summary?.endsWith(".summary.md"))
        assertNull(entries[0].error)

        assertTrue(File(context.home, entries[0].raw!!).exists())
        assertTrue(File(context.home, entries[0].summary!!).exists())
        assertEquals("This is a sample document.", File(context.home, entries[0].summary!!).readText())
    }

    @Test
    fun `delete URL`() {
        // GIVEN
        kb.init(config, context)

        val url = URL("https://pdfobject.com/pdf/sample.pdf")
        kb.ingest(url)
        Thread.sleep(5000) // Wait for the async processing to complete

        // WHEN
        kb.delete(url.toString())

        // THEN
        val entries = kb.entries()
        assertEquals(0, entries.size)
    }
}
