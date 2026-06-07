package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.messaging.SendMessageTool
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PythonToolTest {
    private val tool = PythonTool()
    private val context = Context(
        home = File("/tmp"),
        llm = mock(),
    )

    private fun createFile(name: String, code: String): File {
        val file = File.createTempFile(name, ".py")
        file.writeText(code)
        return file
    }

    @BeforeEach
    fun init() {
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(PythonTool.NAME, meta.name)
        assertEquals(3, meta.parameters.size)

        assertEquals("path", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("working_dir", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)

        assertEquals("timeout", meta.parameters[2].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[2].type)
        assertFalse(meta.parameters[2].required)
    }

    @Test
    fun `exec - print`() {
        val file = createFile("print", "print('Hello, World!')")
        val result = tool.exec(mapOf("path" to file.absolutePath))

        assertEquals("Hello, World!\n", result)
    }

    @Test
    fun `exec - math`() {
        val file = createFile(
            "math",
            """
                        import math

                        number = 16
                        result = math.sqrt(number)

                        print(f"The square root of {number} is {result}")
                    """.trimIndent()
        )
        val result = tool.exec(mapOf("path" to file.absolutePath))

        assertEquals("The square root of 16 is 4.0\n", result)
    }

    @Test
    fun `exec - compute error`() {
        val file = createFile("compute_error", "print(1 / 0)")
        val result = tool.exec(mapOf("path" to file.absolutePath))

        assertEquals(true, result.contains("FAILURE"))
    }

    @Test
    fun `exec - syntax error`() {
        val file = createFile(
            "syntax_error",
            """
                        number = 16
                        result = sqrt(number)

                        print(f"The square root of {number} is {result}")
                    """.trimIndent(),
        )

        val result = tool.exec(mapOf("path" to file.absolutePath))
        assertEquals(true, result.contains("FAILURE"))
    }

    @Test
    fun `exec - empty path`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("oath" to "")) }
    }

    @Test
    fun `exec - no path`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }

    @Test
    fun `exec - timeout`() {
        val file = createFile(
            "timeout",
            """
                        while True:
                            pass
                    """.trimIndent(),
        )

        val result = tool.exec(
            mapOf(
                "path" to file.absolutePath,
                "timeout" to 1, // 1 second timeout for testing
            )
        )
        assertEquals(true, result.contains("TIMEOUT"))
    }

    @Test
    fun statusText() {
        val result = tool.statusText(
            listOf(
                LLMToolCall(
                    name = SendMessageTool.NAME,
                    arguments = mapOf(
                        "code" to "foo.py",
                    )
                )
            )
        )
        assertEquals("Running python code", result)
    }
}
