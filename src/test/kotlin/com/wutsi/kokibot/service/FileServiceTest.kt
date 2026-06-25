package com.wutsi.kokibot.service

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileServiceTest {
    private val service = FileService()
    private val context = Context(
        home = File("target/test-data/file-service"),
        llm = mock(),
        assistant = Assistant(
            name = "test"
        )
    )

    @Test
    fun id() {
        assertEquals(FileService.ID, service.id())
    }

    @Test
    fun health() {
        val health = service.health()
        assertEquals(FileService.ID, health.id)
        assertTrue(health.up)
    }

    @Test
    fun urlPath() {
        service.init(emptyMap<String, Any>(), context)

        val url = service.urlPath(context.home.absolutePath + "/workspace/a/file.pdf")
        assertEquals("/files/test/workspace/a/file.pdf", url)
    }

    @Test
    fun `contentType - txt`() {
        assertEquals("text/plain", service.contentType(File("file.txt")))
    }

    @Test
    fun `contentType - md`() {
        assertEquals("text/markdown", service.contentType(File("file.md")))
    }

    @Test
    fun `contentType - html`() {
        assertEquals("text/html", service.contentType(File("file.html")))
    }

    @Test
    fun `contentType - htm`() {
        assertEquals("text/html", service.contentType(File("file.htm")))
    }

    @Test
    fun `contentType - json`() {
        assertEquals("application/json", service.contentType(File("file.json")))
    }

    @Test
    fun `contentType - xml`() {
        assertEquals("application/xml", service.contentType(File("file.xml")))
    }

    @Test
    fun `contentType - csv`() {
        assertEquals("text/csv", service.contentType(File("file.csv")))
    }

    @Test
    fun `contentType - jpg`() {
        assertEquals("image/jpeg", service.contentType(File("file.jpg")))
    }

    @Test
    fun `contentType - jpeg`() {
        assertEquals("image/jpeg", service.contentType(File("file.jpeg")))
    }

    @Test
    fun `contentType - png`() {
        assertEquals("image/png", service.contentType(File("file.png")))
    }

    @Test
    fun `contentType - gif`() {
        assertEquals("image/gif", service.contentType(File("file.gif")))
    }

    @Test
    fun `contentType - unknown`() {
        assertEquals("application/octet-stream", service.contentType(File("file.ppp")))
    }

    @Test
    fun `urlPath - not in home`() {
        service.init(emptyMap<String, Any>(), context)

        val url = service.urlPath("/workspace/a/file.pdf")
        assertEquals(null, url)
    }
}
