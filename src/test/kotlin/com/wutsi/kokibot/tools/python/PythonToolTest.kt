package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolParameterType
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

    @BeforeEach
    fun init() {
        tool.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(PythonTool.NAME, meta.name)
        assertEquals(2, meta.parameters.size)

        assertEquals("code", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("timeout", meta.parameters[1].name)
        assertEquals(ToolParameterType.INTEGER, meta.parameters[1].type)
    }

    @Test
    fun `exec - print`() {
        val result = tool.exec(mapOf("code" to "print('Hello, World!')"))

        assertEquals("Hello, World!\n", result)
    }

    @Test
    fun `exec - math`() {
        val result = tool.exec(
            mapOf(
                "code" to
                    """
                        import math

                        number = 16
                        result = math.sqrt(number)

                        print(f"The square root of {number} is {result}")
                    """.trimIndent()
            )
        )

        assertEquals("The square root of 16 is 4.0\n", result)
    }

    @Test
    fun `exec - compute error`() {
        val result = tool.exec(
            mapOf(
                "code" to
                    """
                        import math

                        number = -16
                        result = math.sqrt(number)

                        print(f"The square root of {number} is {result}")
                    """.trimIndent()
            )
        )

        assertEquals(true, result.startsWith("Error executing Python code: "))
    }

    @Test
    fun `exec - syntax error`() {
        val result = tool.exec(
            mapOf(
                "code" to
                    """
                        number = 16
                        result = sqrt(number)

                        print(f"The square root of {number} is {result}")
                    """.trimIndent()
            )
        )

        assertEquals(true, result.startsWith("Error executing Python code: "))
    }

    @Test
    fun `exec - empty code`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("code" to "")) }
    }

    @Test
    fun `exec - no code`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }

    @Test
    fun `exec - timeout`() {
        val started = System.currentTimeMillis()
        val result = tool.exec(
            mapOf(
                "code" to "while True:\n    pass\n",
                "timeout" to "1",
            )
        )
        val elapsedMs = System.currentTimeMillis() - started

        assertTrue(
            result.startsWith("Error executing Python code: execution timed out after 1 seconds"),
            "Unexpected result: $result"
        )
        assertTrue(elapsedMs < 10_000, "Execution did not terminate promptly: ${elapsedMs}ms")
    }
}
