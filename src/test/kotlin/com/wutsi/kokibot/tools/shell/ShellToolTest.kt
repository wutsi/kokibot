package com.wutsi.kokibot.tools.shell

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertEquals(3, meta.parameters.size)

        assertEquals("command", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)

        assertEquals("directory", meta.parameters[1].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[1].type)
        assertFalse(meta.parameters[1].required)

        assertEquals("timeout", meta.parameters[2].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[2].type)
        assertFalse(meta.parameters[2].required)
    }

    @Test
    fun exec() {
        val result = tool.exec(mapOf("command" to "ls -la"))

//        println(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `exec with directory`() {
        val result = tool.exec(
            mapOf(
                "command" to "ls -la",
                "directory" to System.getProperty("user.home")
            )
        )

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
    fun `exec - with redirection`() {
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
    fun `exec - timeout`() {
        val started = System.currentTimeMillis()
        val result = tool.exec(
            mapOf(
                "command" to "sleep 30",
                "timeout" to "1",
            )
        )
        val elapsedMs = System.currentTimeMillis() - started

        assertTrue(
            result.contains("timed out after 1 seconds"),
            "Unexpected result: $result"
        )
        assertTrue(elapsedMs < 10_000, "Command did not terminate promptly: ${elapsedMs}ms")
    }

    @Test
    fun `exec - forbidden commands`() {
        val forbiddenCommands = listOf(
            // Direct forbidden commands
            "rm -rf /",
            "rm -fr /tmp",
            "rm --recursive /tmp",
            "mkfs /dev/sda1",
            "mke2fs /dev/sda1",
            "dd if=/dev/zero of=/dev/sda",
            "mv /foo /bar",
            "chmod 777 /etc/passwd",
            "chown root:root /etc/passwd",
            "sudo reboot",
            "echo test > /etc/passwd",
            "echo test >> /dev/sda",
            "echo test > /boot/grub.cfg",
            // Bypass attempts
            "echo hello; rm -rf /data", // semicolon bypass
            "rm    -rf    /data", // extra spaces
            "echo \$(rm -rf /data)", // command substitution
            "echo `chmod 777 /etc/passwd`", // backtick substitution
            "ls | rm -rf /tmp", // pipe chain
            "true && rm -rf /tmp", // && chain
            "false || sudo reboot", // || chain
            "ls & rm -rf /tmp", // background chain
            "/bin/rm -rf /tmp", // absolute executable path
            "FOO=bar sudo ls", // env-var prefix
        )
        forbiddenCommands.forEach { cmd ->
            val result = tool.exec(mapOf("command" to cmd))
            assertEquals(
                true,
                result.contains("FORBIDDEN"),
            )
        }
    }
}
