package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellUtilTest {
    @Test
    fun exists() {
        assertTrue(ShellUtil.exists("java"))
        assertFalse(ShellUtil.exists("x0x0x0x"))
    }
}
