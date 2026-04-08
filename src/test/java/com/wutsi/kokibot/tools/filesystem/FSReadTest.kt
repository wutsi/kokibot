package com.wutsi.kokibot.tools.filesystem

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class FSReadTest {
    private val tool = FSRead()

    @Test
    fun metadata() {
        val metadata = tool.metadata()
        assertEquals(FSRead.NAME, metadata.name)
        assertEquals(1, metadata.parameters.size)

        assertEquals("path", metadata.parameters[0].name)
        assertEquals(ToolParameterType.STRING, metadata.parameters[0].type)
        assertTrue(metadata.parameters[0].required)
    }

    @Test
    fun exec() {
        // GIVEN
        val file = kotlin.io.path.createTempFile().toFile()
        file.writeText("Hello, World!")

        // WHEN
        val result = tool.exec(mapOf("path" to file.absolutePath))

        // THEN
        assertEquals("Hello, World!", result)
        file.delete()
    }

    @Test
    fun `exec - file doesnt exist`() {
        // WHEN
        val result = tool.exec(mapOf("path" to "non-existing-file.txt"))

        // THEN
        assertEquals("File not found: non-existing-file.txt", result)
    }

    @Test
    fun `exec - file is a directory`() {
        // GIVEN
        val dir = kotlin.io.path.createTempDirectory().toFile()

        // WHEN
        val result = tool.exec(mapOf("path" to dir.absolutePath))

        // THEN
        assertEquals("Not a file: ${dir.absolutePath}", result)
        dir.delete()
    }

    @Test
    fun `exec - no path`() {
        // WHEN
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }
}
