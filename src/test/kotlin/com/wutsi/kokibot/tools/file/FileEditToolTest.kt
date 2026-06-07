package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeText

class FileEditToolTest {
    private val tool = FileEditTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(FileEditTool.NAME, meta.name)
        assertEquals(3, meta.parameters.size)

        assertEquals("path", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("search", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertTrue(meta.parameters[1].required)

        assertEquals("replace", meta.parameters[2].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[2].type)
        assertTrue(meta.parameters[2].required)
    }

    @Test
    fun exec() {
        val file = Files.createTempFile("happy-path", ".txt")
        file.writeText("Hello World")

        val result = tool.exec(
            mapOf(
                "path" to file.toString(),
                "search" to "Hello",
                "replace" to "Hi",
            )
        )
        assertEquals("SUCCESS. File updated.", result)
        assertEquals("Hi World", file.toFile().readText())
    }

    @Test
    fun `exec - search block not found`() {
        val file = Files.createTempFile("search-block-not-found", ".txt")
        file.writeText("Hello World")

        val result = tool.exec(
            mapOf(
                "path" to file.toString(),
                "search" to "Hi",
                "replace" to "Hello",
            )
        )
        assertEquals("FAILURE. Search block not found. Ensure whitespace/indentation matches exactly.", result)
        assertEquals("Hello World", file.toFile().readText())
    }

    @Test
    fun `exec - file not found`() {
        val result = tool.exec(
            mapOf(
                "path" to "non-existing-file.txt",
                "search" to "Hello",
                "replace" to "Hi",
            )
        )
        assertEquals("FAILURE. File not found.", result)
    }

    @Test
    fun `exec - search block not unique`() {
        val file = Files.createTempFile("search-block-not-unique", ".txt")
        file.writeText("Hello World\nHello Again")

        val result = tool.exec(
            mapOf(
                "path" to file.toString(),
                "search" to "Hello",
                "replace" to "Hi",
            )
        )
        assertEquals("FAILURE. Search block is not unique (2 matches found). Provide more context.", result)
        assertEquals("Hello World\nHello Again", file.toFile().readText())
    }

    @Test
    fun `exec - search block with context`() {
        val file = Files.createTempFile("search-block-not-unique", ".txt")
        file.writeText("Hello World\nHello Again")

        val result = tool.exec(
            mapOf(
                "path" to file.toString(),
                "search" to "Hello World",
                "replace" to "Hi World",
            )
        )
        assertEquals("SUCCESS. File updated.", result)
    }

    @Test
    fun `exec - search block too short`() {
        val file = Files.createTempFile("search-block-too-short", ".txt")
        file.writeText("Hello World")

        val result = tool.exec(
            mapOf(
                "path" to file.toString(),
                "search" to "o W",
                "replace" to "o Wonderful ",
            )
        )
        assertEquals(
            "FAILURE. Search block too short to be safe. Search block should be at least 5 characters long.",
            result
        )
        assertEquals("Hello World", file.toFile().readText())
    }

    @Test
    fun `statusText - accessing memory`() {
        val context = Context(
            home = File("target/file-edit-tool"),
            llm = mock(),
        )
        tool.init(mapOf("" to ""), context)

        val result = tool.statusText(
            listOf(
                LLMToolCall(
                    name = FileEditTool.NAME,
                    arguments = mapOf(
                        "path" to "${context.home.absolutePath}/memory/MEMORY.md",
                    )
                )
            )
        )
        assertEquals("Updating memory", result)
    }

    @Test
    fun statusText() {
        val context = Context(
            home = File("target/file-edit-tool"),
            llm = mock(),
        )
        tool.init(mapOf("" to ""), context)

        val result = tool.statusText(
            listOf(
                LLMToolCall(
                    name = FileEditTool.NAME,
                    arguments = mapOf(
                        "path" to "/foo/bar.md",
                    )
                )
            )
        )
        assertEquals(true, result.contains("/foo/bar.md"))
    }
}
