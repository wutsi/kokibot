package com.wutsi.kokibot.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringUtilTest {

    @Test
    fun `take returns full text when shorter than limit`() {
        val text = "Short text"
        val result = StringUtil.take(text, 200)

        assertEquals("Short text", result)
    }

    @Test
    fun `take truncates text and adds ellipsis when longer than limit`() {
        val text = "A".repeat(300)
        val result = StringUtil.take(text, 200)

        assertEquals("A".repeat(200) + "...", result)
    }

    @Test
    fun `take replaces newlines with spaces`() {
        val text = "Line 1\nLine 2\nLine 3"
        val result = StringUtil.take(text, 200)

        assertEquals("Line 1 Line 2 Line 3", result)
    }

    @Test
    fun `take trims whitespace`() {
        val text = "  Text with spaces  \n\n"
        val result = StringUtil.take(text, 200)

        assertEquals("Text with spaces", result)
    }

    @Test
    fun `take handles text exactly at limit`() {
        val text = "A".repeat(200)
        val result = StringUtil.take(text, 200)

        assertEquals("A".repeat(200), result)
    }

    @Test
    fun `take with custom limit`() {
        val text = "This is a longer text"
        val result = StringUtil.take(text, 10)

        assertEquals("This is a...", result)
    }

    @Test
    fun `take uses default limit of 200`() {
        val text = "A".repeat(300)
        val result = StringUtil.take(text)

        assertEquals("A".repeat(200) + "...", result)
    }
}
