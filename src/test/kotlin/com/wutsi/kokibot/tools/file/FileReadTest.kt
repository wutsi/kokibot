package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FileReadTest {
    val tool = FileRead()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assert(meta.name == FileRead.NAME)
        assertEquals(2, meta.parameters.size)
        assertEquals("path", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
        assertEquals("max_length", meta.parameters[1].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)
    }

    @Test
    fun exec() {
        val args = mapOf("path" to this::class.java.getResource("/file/sample.txt")?.file)
        val result = tool.exec(args)
        assertTrue(result.contains("Content of TXT file"))
    }

    @Test
    fun `exec - max length`() {
        val args = mapOf(
            "path" to this::class.java.getResource("/file/sample.txt")?.file,
            "max_length" to 10
        )
        val result = tool.exec(args)
        assertTrue(result.contains("Content of"))
        assertFalse(result.contains("Content of TXT file"))
    }

    @Test
    fun `exec - file not found`() {
        val args = mapOf(
            "path" to "/file/xxx/not-found.txt",
        )
        val result = tool.exec(args)
        assertTrue(result.contains("File not found"))
    }

    @Test
    fun `exec - directory`() {
        val args = mapOf(
            "path" to this::class.java.getResource("/file")?.file,
        )
        val result = tool.exec(args)
        assertTrue(result.contains("Not a file"))
    }

    @Test
    fun `exec - no path`() {
        val args = emptyMap<String, Any>()
        assertThrows<IllegalArgumentException> { tool.exec(args) }
    }
}
