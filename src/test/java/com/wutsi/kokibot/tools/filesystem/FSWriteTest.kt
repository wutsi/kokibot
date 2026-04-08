package com.wutsi.kokibot.tools.filesystem

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FSWriteTest {
    private val tool = FSWrite()

    @Test
    fun metadata() {
        val metadata = tool.metadata()
        assertEquals(FSWrite.NAME, metadata.name)
        assertEquals(2, metadata.parameters.size)

        assertEquals("path", metadata.parameters[0].name)
        assertEquals(ToolParameterType.STRING, metadata.parameters[0].type)
        assertTrue(metadata.parameters[0].required)

        assertEquals("content", metadata.parameters[1].name)
        assertEquals(ToolParameterType.STRING, metadata.parameters[1].type)
        assertFalse(metadata.parameters[1].required)
    }

    @Test
    fun exec() {
        // GIVEN
        val file = kotlin.io.path.createTempFile().toFile()

        // WHEN
        val result = tool.exec(mapOf("path" to file.absolutePath, "content" to "Hello, World!"))

        // THEN
        assertEquals("File stored: ${file.absolutePath}", result)
        assertEquals("Hello, World!", file.readText())
        file.delete()
    }

    @Test
    fun `exec - no content`() {
        // GIVEN
        val file = kotlin.io.path.createTempFile().toFile()

        // WHEN
        val result = tool.exec(mapOf("path" to file.absolutePath))

        // THEN
        assertEquals("File stored: ${file.absolutePath}", result)
        assertEquals("", file.readText())
        file.delete()
    }

    @Test
    fun `exec - no path`() {
        // WHEN
        assertThrows<IllegalArgumentException> {
            tool.exec(mapOf("content" to "Hello, World!"))
        }
    }

    @Test
    fun `exec - create dirs`() {
        // WHEN
        val result = tool.exec(mapOf("path" to "/invalid/path/file.txt", "content" to "Hello, World!"))

        // THEN
        assertTrue(result.startsWith("Unable to write the file: /invalid/path/file.txt"))
    }
}
