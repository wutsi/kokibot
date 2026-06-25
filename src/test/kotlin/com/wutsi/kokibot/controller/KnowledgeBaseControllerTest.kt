package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.kb.FileAlreadyIngestedException
import com.wutsi.kokibot.service.kb.KBEntry
import com.wutsi.kokibot.service.kb.KnowledgeBase
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.LinkedMultiValueMap
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class KnowledgeBaseControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun `get knowledge-base`() {
        doReturn(listOf(createBootstrap("007", enabled = true, exclusive = false))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/knowledge-base", Map::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["enabled"])
        assertEquals(false, response.body!!["exclusive"])
    }

    @Test
    fun `get knowledge-base - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/knowledge-base", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set knowledge-base setting`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/knowledge-base/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("knowledge-base.enabled", false)
    }

    @Test
    fun `set knowledge-base setting - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/xxx/knowledge-base/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set knowledge-base setting - bad request when missing key`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/knowledge-base/settings",
            mapOf("value" to false),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `set knowledge-base setting - bad request on unknown key`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(ConfigurationException("Unknown knowledge-base setting: invalid")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/knowledge-base/settings",
            mapOf("key" to "invalid", "value" to "x"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("Unknown knowledge-base setting: invalid", (response.body as Map<*, *>)["error"])
    }

    @Test
    fun `get entries`() {
        val entries = listOf(
            KBEntry(name = "doc.pdf", scope = "Technical spec", keywords = listOf("kotlin", "spring")),
            KBEntry(name = "readme.md", scope = "Overview", keywords = listOf("setup")),
        )
        doReturn(listOf(createBootstrap("007", entries = entries))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/knowledge-base/entries", List::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body as List<*>
        assertEquals(2, body.size)
        val first = body[0] as Map<*, *>
        assertEquals("doc.pdf", first["filename"])
        assertEquals("Technical spec", first["scope"])
        assertEquals(listOf("kotlin", "spring"), first["keywords"])
    }

    @Test
    fun `get entries - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/knowledge-base/entries", List::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `upload file`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val body = LinkedMultiValueMap<String, Any>()
        body.add(
            "file",
            object : ByteArrayResource("hello".toByteArray()) {
                override fun getFilename() = "test.txt"
            },
        )
        val response = rest.postForEntity(
            "/assistants/007/knowledge-base/upload",
            HttpEntity(body, headers),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap.getContext().knowledgeBase).ingest(any())
    }

    @Test
    fun `upload file - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val body = LinkedMultiValueMap<String, Any>()
        body.add(
            "file",
            object : ByteArrayResource("hello".toByteArray()) {
                override fun getFilename() = "test.txt"
            },
        )
        val response = rest.postForEntity(
            "/assistants/xxx/knowledge-base/upload",
            HttpEntity(body, headers),
            Map::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `upload file - conflict when already ingested`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        val kb = bootstrap.getContext().knowledgeBase
        doThrow(FileAlreadyIngestedException("test.txt is already ingested")).whenever(kb).ingest(any())

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val body = LinkedMultiValueMap<String, Any>()
        body.add(
            "file",
            object : ByteArrayResource("hello".toByteArray()) {
                override fun getFilename() = "test.txt"
            },
        )
        val response = rest.postForEntity(
            "/assistants/007/knowledge-base/upload",
            HttpEntity(body, headers),
            Map::class.java,
        )

        assertEquals(409, response.statusCode.value())
        assertEquals("test.txt is already ingested", (response.body as Map<*, *>)["error"])
    }

    @Test
    fun `delete entry`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        rest.getForEntity("/assistants/007/knowledge-base/entries/delete?filename=doc.pdf", Void::class.java)

        verify(bootstrap.getContext().knowledgeBase).delete("doc.pdf")
    }

    @Test
    fun `delete entry - not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity(
            "/assistants/xxx/knowledge-base/entries/delete?filename=doc.pdf",
            Void::class.java,
        )

        assertEquals(200, response.statusCode.value())
    }

    private fun createBootstrap(
        name: String,
        enabled: Boolean = true,
        exclusive: Boolean = true,
        entries: List<KBEntry> = emptyList()
    ): Bootstrap {
        val kb = mock<KnowledgeBase>()
        doReturn(enabled).whenever(kb).isEnabled()
        doReturn(exclusive).whenever(kb).isExclusive()
        doReturn(entries).whenever(kb).readIndex()

        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val context = Context(
            assistant = assistant,
            home = File("target/kb-controller/$name"),
            llm = mock(),
            knowledgeBase = kb,
        )
        context.init(mapOf("y" to "y"))

        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
