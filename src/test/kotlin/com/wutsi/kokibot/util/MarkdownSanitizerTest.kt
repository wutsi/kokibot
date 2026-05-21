package com.wutsi.kokibot.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownSanitizerTest {
    @Test
    fun escape() {
        val input = "This is a *test* with [markdown] (characters) that need to be escaped."
        val expected = "This is a \\*test\\* with [markdown] (characters) that need to be escaped."
        val result = MarkdownUtil.escape(input)
        assertEquals(expected, result)
    }
}
