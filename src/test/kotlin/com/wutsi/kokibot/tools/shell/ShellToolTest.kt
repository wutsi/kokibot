package com.wutsi.kokibot.tools.shell

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellToolTest {
    private val tool = ShellTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(ShellTool.Companion.NAME, meta.name)
        assertEquals(1, meta.parameters.size)

        assertEquals("command", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun exec() {
        val result = tool.exec(mapOf("command" to "ls -la"))

//        println(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `exec - bad command`() {
        val result = tool.exec(mapOf("command" to "xx -la"))

//        println(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `exec - with redirected`() {
        val result = tool.exec(mapOf("command" to "find ./ -type f 2>/dev/null | wc -l"))

//        println(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `exec - empty command`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("command" to "")) }
    }

    @Test
    fun `exec - no command`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }

    @Test
    fun `exec - forbidden sudo`() {
        val result = tool.exec(mapOf("command" to "sudo ls -la"))
        assertEquals(ShellTool.ERROR_FORBIDDEN, result)
    }

    @Test
    fun `exec - forbidden rm`() {
        val result = tool.exec(mapOf("command" to "rm -rf /tmp/test.txt"))
        assertEquals(ShellTool.ERROR_FORBIDDEN, result)
    }

    @Test
    fun `exec - forbidden chmod`() {
        val result = tool.exec(mapOf("command" to "chmod +x /tmp/test.txt"))
        assertEquals(ShellTool.ERROR_FORBIDDEN, result)
    }

    @Test
    fun `exec - forbidden chmown`() {
        val result = tool.exec(mapOf("command" to "chown foo:bar /tmp/test.txt"))
        assertEquals(ShellTool.ERROR_FORBIDDEN, result)
    }

    @Test
    fun `exec - forbidden to etc`() {
        val result = tool.exec(mapOf("command" to "ls -la > /etc/test.txt"))
        assertEquals(ShellTool.ERROR_FORBIDDEN, result)
    }
}
