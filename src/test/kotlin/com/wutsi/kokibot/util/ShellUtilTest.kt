package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellUtilTest {
    @Test
    fun exists() {
        assertTrue(ShellUtil.exists("java"))
        assertFalse(ShellUtil.exists("x0x0x0x"))
    }

    @Test
    fun `exec - success`() {
        val result = ShellUtil.exec("ls -ls")
        println(result.output)

        assertEquals(0, result.status)
        assertNotNull(result.output)
        assertNull(result.error)
    }

    @Test
    fun `exec - failure`() {
        val result = ShellUtil.exec("__ls__ -ls")
        println(result.output)
        println(result.error)

        assertTrue(result.status != 0)
        assertNull(result.output)
        assertNotNull(result.error)
    }

    @Test
    fun `exec - not timed out`() {
        val result = ShellUtil.exec("which java", timeoutSeconds = 50)
        println(result.output)

        assertEquals(0, result.status)
        assertNotNull(result.output)
        assertNull(result.error)
    }

    @Test
    fun `exec - timed out`() {
        val result = ShellUtil.exec("find / -name x0x0x0x", timeoutSeconds = 1)
        println(result.output)

        assertEquals(-1, result.status)
        assertNull(result.output)
        assertEquals(true, result.error?.contains("TIMEOUT"))
    }
}
