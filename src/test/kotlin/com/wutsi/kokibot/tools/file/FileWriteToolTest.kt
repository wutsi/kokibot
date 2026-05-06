package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class FileWriteToolTest {
    private val tool = FileWriteTool()

    @TempDir
    lateinit var tempDir: Path

    // ...existing code...
    @Test
    fun metadata() {
        val meta = tool.metadata()
        assert(meta.name == FileWriteTool.NAME)
        assertEquals(3, meta.parameters.size)
        assertEquals("path", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
        assertEquals("content", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)
        assertTrue(meta.parameters[0].required)
        assertEquals("overwrite", meta.parameters[2].name)
        assertEquals(ToolParameterType.BOOLEAN, meta.parameters[2].type)
        assertFalse(meta.parameters[2].required)
    }

    @Test
    fun `exec - writes new file`() {
        val path = tempDir.resolve("hello.txt").toString()

        val result = tool.exec(
            mapOf(
                "path" to path,
                "content" to "Hello, World!",
            )
        )

        assertEquals(true, result.contains("Success"))
        assertEquals("Hello, World!", File(path).readText())
    }

    @Test
    fun `exec - creates parent directories`() {
        val path = tempDir.resolve("a/b/c/nested.txt").toString()

        val result = tool.exec(
            mapOf(
                "path" to path,
                "content" to "nested",
            )
        )

        assertEquals(true, result.contains("Success"))
        assertTrue(File(path).exists())
        assertEquals("nested", File(path).readText())
    }

    @Test
    fun `exec - empty content when not provided`() {
        val path = tempDir.resolve("empty.txt").toString()

        val result = tool.exec(mapOf("path" to path))

        assertEquals(true, result.contains("Success"))
        assertEquals("", File(path).readText())
    }

    @Test
    fun `exec - does not overwrite existing file by default`() {
        val file = tempDir.resolve("existing.txt").toFile()
        file.writeText("original")

        val result = tool.exec(
            mapOf(
                "path" to file.absolutePath,
                "content" to "new",
            )
        )

        assertEquals(true, result.contains("File already exists: ${file.absolutePath}"))
        assertEquals("original", file.readText())
    }

    @Test
    fun `exec - overwrites existing file when overwrite=true`() {
        val file = tempDir.resolve("existing.txt").toFile()
        file.writeText("original")

        val result = tool.exec(
            mapOf(
                "path" to file.absolutePath,
                "content" to "new",
                "overwrite" to true,
            )
        )

        assertEquals(true, result.contains("Success"))
        assertEquals("new", file.readText())
    }

    @Test
    fun `exec - rejects writing to a directory path`() {
        val dir = tempDir.toFile()

        val result = tool.exec(
            mapOf(
                "path" to dir.absolutePath,
                "content" to "x",
                "overwrite" to true,
            )
        )

        assertEquals(true, result.contains("Not a file: ${dir.absolutePath}"))
    }

    @Test
    fun `exec - returns failure message on IO error`() {
        // A path under a regular file (not a directory) cannot have children created
        val parent = tempDir.resolve("not-a-dir").toFile()
        parent.writeText("blocker")
        val path = parent.resolve("child.txt").absolutePath

        val result = tool.exec(
            mapOf(
                "path" to path,
                "content" to "x",
            )
        )

        assertTrue(
            result.contains("Failed to read file. Error="),
        )
    }

    @Test
    fun `exec - missing path throws`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }

    @Test
    fun `exec - empty path throws`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("path" to "")) }
    }
}
