package com.wutsi.kokibot.tools.filesystem

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.service.file.TextExtractor
import com.wutsi.kokibot.service.file.TextExtractorFactory
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FSReadTest {
    private val factory = mock<TextExtractorFactory>()
    private val tool = FSRead(factory)

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(FSRead.NAME, meta.name)
        assertEquals(1, meta.parameters.size)

        assertEquals("file", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun `exec - pdf`() {
        val extractor = mock<TextExtractor>()
        doReturn("Hello, World!").whenever(extractor).extract(any())
        doReturn(extractor).whenever(factory).create(any())

        tool.init(emptyMap<String, Any>(), mock())
        val result = tool.exec(mapOf("file" to "/path/to/file.pdf"))

        assertEquals("Hello, World!", result)
    }

    @Test
    fun `exec - json`() {
        val result = tool.exec(mapOf("file" to this::class.java.getResource("/file/sample.json")!!.file))

        assertEquals("{\n  \"foo\": 2\n}\n", result)
    }

    @Test
    fun `exec - txt`() {
        val result = tool.exec(mapOf("file" to this::class.java.getResource("/file/sample.txt")!!.file))

        assertEquals("Content of TXT file\n", result)
    }

    @Test
    fun `exec - missing param`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("xxx" to "/path/to/file.pdf")) }
    }

    @Test
    fun `exec - error`() {
        doThrow(IllegalStateException("Failure")).whenever(factory).create(any())

        val result = tool.exec(mapOf("file" to "/path/to/file.pdf"))

        assertEquals("Unable to extract the content of /path/to/file.pdf: Failure", result)
    }
}
