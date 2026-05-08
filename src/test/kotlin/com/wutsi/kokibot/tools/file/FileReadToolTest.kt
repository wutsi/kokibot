package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals

class FileReadToolTest {
    val tool = FileReadTool()
    val context = Context(
        home = File("target/test-data/web-fetch-tool"),
        llm = mock(),
    )

    @BeforeEach
    fun setUp() {
        tool.init(emptyMap<String, String>(), context)
        context.fileService.init(mapOf("foo" to "bar"), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assert(meta.name == FileReadTool.NAME)
        assertEquals(1, meta.parameters.size)
        assertEquals("path", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun exec() {
        val args = mapOf("path" to this::class.java.getResource("/file/sample.txt")?.file)
        val result = tool.exec(args)
        assertTrue(result.contains("Content of TXT file"))
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

    @Test
    fun `exec - empty path`() {
        val args = mapOf("path" to "")
        assertThrows<IllegalArgumentException> { tool.exec(args) }
    }

    @Test
    fun `exec - null path`() {
        val args = mapOf("path" to null)
        assertThrows<IllegalArgumentException> { tool.exec(args) }
    }

    @Test
    fun `exec - file not readable`() {
        val file = java.io.File.createTempFile("test", ".txt")
        file.setReadable(false)
        val args = mapOf("path" to file.absolutePath)
        val result = tool.exec(args)
        assertTrue(result.contains("File is not readable"))
    }

    @Test
    fun `exec - unsupported file type`() {
        val args = mapOf(
            "path" to this::class.java.getResource("/file/medic.png")?.file,
        )
        val result = tool.exec(args)
        assertTrue(result.contains("FAILURE"))
    }

    @Test
    fun `exec - file too large to convert`() {
        val tool = FileReadTool(7) // Set maxLength to 100 characters to force conversion failure
        tool.init(emptyMap<String, String>(), context)

        val file = File(this::class.java.getResource("/file/sample.txt")!!.file)
        val args = mapOf(
            "path" to file.absolutePath,
        )
        val result = tool.exec(args)

        assertTrue(result.contains("Content"))
        assertFalse(result.contains("Content of TXT file"))
    }
}
