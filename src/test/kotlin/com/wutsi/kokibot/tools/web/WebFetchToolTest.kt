package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class WebFetchToolTest {
    val tool = WebFetchTool()
    val context = Context(
        home = File("target/test-data/web-fetch-tool"),
        llm = mock(),
    )

    @BeforeEach
    fun setUp() {
        tool.init(mapOf("foo" to "bar"), context)
        context.fileService.init(mapOf("foo" to "bar"), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(WebFetchTool.NAME, meta.name)
        assertEquals(2, meta.parameters.size)
        assertEquals("url", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
        assertEquals("max_length", meta.parameters[1].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)
    }

    @Test
    fun id() {
        assertEquals("tool:web_fetch", tool.id())
    }

    @Test
    fun health() {
        val health = tool.health()
        assertTrue(health.up)
        assertEquals(tool.id(), health.id)
        assertNull(health.details)
    }

    @Test
    fun exec() {
        val args = mapOf("url" to "https://evendo.com/locations/cameroon/yaounde/odza")
        val result = tool.exec(args)
        println(result)
        assertTrue(result.contains("Odza: A Tranquil Retreat in the Bustling Heart of Yaoundé"))
    }

    @Test
    fun `exec PDF`() {
        val args = mapOf("url" to "https://www.amicaall.org/publications/profiles/Profil_municipal%20Soa_finalise.pdf")
        val result = tool.exec(args)
        println(result)
        assertTrue(result.contains("Collège d’enseignement secondaire"))
    }

    @Test
    fun `exec JSON`() {
        val args =
            mapOf("url" to "https://raw.githubusercontent.com/wutsi/kokibot/refs/heads/master/renovate.json")
        val result = tool.exec(args)
        assertTrue(result.contains("config:base"))
    }

    @Test
    fun `exec TXT`() {
        val args =
            mapOf("url" to "https://example-files.online-convert.com/document/txt/example.txt")
        val result = tool.exec(args)
        assertTrue(result.contains("The names \"John Doe\" for males, \"Jane Doe\" or \"Jane Roe\" for females"))
    }

//    @Test
//    fun `exec - DOC`() {
//        val args = mapOf("url" to "https://podcasts.ceu.edu/sites/podcasts.ceu.edu/files/sample.doc")
//        val result = tool.exec(args)
//        assertTrue(result.contains("Instructions about final paper"))
//    }

    @Test
    fun `exec - DOCX`() {
        val args = mapOf("url" to "https://calibre-ebook.com/downloads/demos/demo.docx")
        val result = tool.exec(args)
        assertTrue(result.contains("This document demonstrates the ability"))
    }

    @Test
    fun `exec - XLS`() {
        val args = mapOf("url" to "https://www.cmu.edu/blackboard/files/evaluate/tests-example.xls")
        val result = tool.exec(args)
        assertTrue(result.contains("File Information"))
    }

    @Test
    fun `exec - XLSX`() {
        val args = mapOf("url" to "https://www.ou.edu/content/dam/cms/docs/sample-excel-file.xlsx")
        val result = tool.exec(args)
        assertTrue(result.contains("Sample Excel File"))
    }

    @Test
    fun `exec - XML`() {
        val args = mapOf("url" to "https://raw.githubusercontent.com/wutsi/kokibot/refs/heads/master/pom.xml")
        val result = tool.exec(args)
        assertTrue(result.contains("<artifactId>kokibot</artifactId>"))
    }

    @Test
    fun `exec - Zip`() {
        val args = mapOf("url" to "https://github.com/wutsi/kokibot/releases/download/v0.0.14/kokibot.zip")
        val result = tool.exec(args)
        assertTrue(result.contains("File: kokibot/HEARTBEAT.md"))
    }

    @Test
    fun `exec - content not found`() {
        val args = mapOf("url" to "https://invalid-url")
        val result = tool.exec(args)
        assertTrue(result.contains("Failed to fetch content from"))
    }

    @Test
    fun `exec - invalid URL`() {
        val args = mapOf("url" to "ftp://google.com")
        val result = tool.exec(args)
        assertTrue(result.contains("Invalid URL:"))
    }

    @Test
    fun `exec - not found`() {
        val args = mapOf("url" to "https://www.microsoft.com/not-found-url-123456789")
        val result = tool.exec(args)
        println(result)
        assertTrue(result.contains("Failed to fetch content from https://www.microsoft.com/not-found-url-123456789"))
    }

    @Test
    fun `exec - empty command`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("url" to "")) }
    }

    @Test
    fun `exec - no command`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }
}
