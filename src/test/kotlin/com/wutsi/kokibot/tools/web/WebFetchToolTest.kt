package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
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
        assertEquals(1, meta.parameters.size)
        assertEquals("url", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
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
    fun `statusText - no tool calls`() {
        val result = tool.statusText(emptyList())
        assertEquals(true, result.contains("Reading online from"))
    }

    @Test
    fun `statusText - single tool call`() {
        val toolCalls = listOf(
            LLMToolCall(name = WebFetchTool.NAME, arguments = mapOf("url" to "https://example.com"))
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Reading online from https://example.com", result)
    }

    @Test
    fun `statusText - multiple tool calls`() {
        val toolCalls = listOf(
            LLMToolCall(name = WebFetchTool.NAME, arguments = mapOf("url" to "https://example.com/a")),
            LLMToolCall(name = WebFetchTool.NAME, arguments = mapOf("url" to "https://example.com/b")),
            LLMToolCall(name = WebFetchTool.NAME, arguments = mapOf("url" to "https://example.ca/c"))
        )
        val result = tool.statusText(toolCalls)
        assertEquals("Reading online from example.com, example.ca", result)
    }

    // Helper: extract the saved file path from exec result and return its content
    private fun getFileContent(result: String): String {
        val path = result.substringAfter("and saved to ")
        return File(path).readText()
    }

    @Test
    fun exec() {
        val url = "https://evendo.com/locations/cameroon/yaounde/odza"
        val result = tool.exec(mapOf("url" to url))
        println(result)

        assertTrue(result.contains("Content fetched from $url and saved to"))

        val content = getFileContent(result)
        assertTrue(content.contains("Odza: A Tranquil Retreat in the Bustling Heart of Yaoundé"))
    }

    @Test
    fun `exec - content too large will be cut off`() {
        val tool = WebFetchTool(100)
        tool.init(mapOf("foo" to "bar"), context)

        val args = mapOf("url" to "https://www.gutenberg.org/files/2600/2600-0.txt")
        val result = tool.exec(args)
        assertEquals(100, getFileContent(result).length)
    }

    @Test
    fun `exec PDF`() {
        val url = "http://tybbot.free.fr/Tybbow/Livres/Autre/moby_dick.pdf"
        val result = tool.exec(mapOf("url" to url))
        println(result)
        assertTrue(result.contains("Content fetched from $url and saved to"))
        val content = getFileContent(result)
        assertTrue(content.contains("La jambe d’Achab", true))
    }

    @Test
    fun `exec JSON`() {
        val url = "https://raw.githubusercontent.com/wutsi/kokibot/refs/heads/master/renovate.json"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"))
        val content = getFileContent(result)
        assertTrue(content.contains("config:base"))
    }

    @Test
    fun `exec TXT`() {
        val url = "https://example-files.online-convert.com/document/txt/example.txt"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"))
        val content = getFileContent(result)
        assertTrue(content.contains("John Doe"))
        assertTrue(content.contains("Jane Doe"))
    }

    @Test
    fun `exec - DOCX`() {
        val url = "https://calibre-ebook.com/downloads/demos/demo.docx"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"), result)
        val content = getFileContent(result)
        assertTrue(content.contains("This document demonstrates the ability"))
    }

    @Test
    fun `exec - XLS`() {
        val url = "https://www.cmu.edu/blackboard/files/evaluate/tests-example.xls"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"), result)
        val content = getFileContent(result)
        assertTrue(content.contains("File Information"))
    }

    @Test
    fun `exec - XLSX`() {
        val url = "https://www.ou.edu/content/dam/cms/docs/sample-excel-file.xlsx"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"), result)
        val content = getFileContent(result)
        assertTrue(content.contains("Sample Excel File"))
    }

    @Test
    fun `exec - XML`() {
        val url = "https://raw.githubusercontent.com/wutsi/kokibot/refs/heads/master/pom.xml"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"), result)
        val content = getFileContent(result)
        assertTrue(content.contains("<artifactId>kokibot</artifactId>"))
    }

    @Test
    fun `exec - Zip`() {
        // kokibot.zip is ~115 MB, which exceeds the 50 MB MAX_FILE_SIZE limit
        val args = mapOf("url" to "https://github.com/wutsi/kokibot/releases/download/v0.0.14/kokibot.zip")
        val result = tool.exec(args)
        assertTrue(
            result.contains("exceeds maximum allowed size"),
            "Expected size-limit rejection but got: $result"
        )
    }

    @Test
    fun `exec - content not found`() {
        val args = mapOf("url" to "https://invalid-url")
        val result = tool.exec(args)
        assertTrue(result.contains("Failed to fetch content from"))
    }

    @Test
    fun `exec - invalid URL`() {
        val url = "ftp://google.com"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Content fetched from $url and saved to"))
        val content = getFileContent(result)
        assertTrue(content.contains("Invalid LINK:"))
    }

    @Test
    fun `exec - not found`() {
        val url = "https://www.microsoft.com/not-found-url-123456789"
        val result = tool.exec(mapOf("url" to url))
        assertTrue(result.contains("Failed to fetch content from $url"))
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
