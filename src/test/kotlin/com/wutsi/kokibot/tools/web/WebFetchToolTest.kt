package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WebFetchToolTest {
    val tool = WebFetchTool()

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
    fun exec() {
        val args = mapOf("url" to "https://evendo.com/locations/cameroon/yaounde/odza")
        val result = tool.exec(args)
        assertTrue(result.contains("Odza: A Tranquil Retreat in the Bustling Heart of Yaoundé"))
    }

    @Test
    fun `exec PDF`() {
        val args = mapOf("url" to "https://www.amicaall.org/publications/profiles/Profil_municipal%20Soa_finalise.pdf")
        val result = tool.exec(args)
        assertTrue(result.contains("La prise en charge des personnes infectées est limitée au counselling"))
    }

    @Test
    fun `exec JSON`() {
        val args =
            mapOf("url" to "https://gist.githubusercontent.com/gcollazo/884a489a50aec7b53765405f40c6fbd1/raw/49d1568c34090587ac82e80612a9c350108b62c5/sample.json")
        val result = tool.exec(args)
//        println(result)
    }

    @Test
    fun `exec TXT`() {
        val args =
            mapOf("url" to "https://example-files.online-convert.com/document/txt/example.txt")
        val result = tool.exec(args)
        assertTrue(result.contains("The names \"John Doe\" for males, \"Jane Doe\" or \"Jane Roe\" for females"))
    }

    @Test
    fun `exec - DOC`() {
        val args = mapOf("url" to "https://podcasts.ceu.edu/sites/podcasts.ceu.edu/files/sample.doc")
        val result = tool.exec(args)
        assertTrue(result.contains("Instructions about final paper and figure submissions in this document are for IEEE"))
    }

    @Test
    fun `exec - DOCX`() {
        val args = mapOf("url" to "https://calibre-ebook.com/downloads/demos/demo.docx")
        val result = tool.exec(args)
        assertTrue(result.contains("This document demonstrates the ability"))
    }

    @Test
    fun `exec - invalid URL`() {
        val args = mapOf("url" to "https://invalid-url")
        val result = tool.exec(args)
        assertTrue(result.contains("Failed to fetch content from https://invalid-url"))
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
