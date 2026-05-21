package com.wutsi.kokibot.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MarkdownUtilTest {
    @Test
    fun `escape reserved characters`() {
        val result = MarkdownUtil.escape("hello *world* _foo_ `bar` |baz|")
        assertEquals("hello \\*world\\* \\_foo\\_ \\`bar\\` \\|baz\\|", result)
    }

    @Test
    fun `split returns single chunk when text fits`() {
        val text = "Hello world"
        val chunks = MarkdownUtil.split(text, 100)
        assertEquals(listOf(text), chunks)
    }

    @Test
    fun `split throws when maxLength is not positive`() {
        assertThrows<IllegalArgumentException> {
            MarkdownUtil.split("abc", 0)
        }
    }

    @Test
    fun `split breaks on paragraph boundary`() {
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val chunks = MarkdownUtil.split(text, 25)

        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 25, "chunk too long: '$it' (${it.length})") }
        assertEquals(
            text.replace("\n\n", " ").replace(" ", ""),
            chunks.joinToString("").replace("\n", "").replace(" ", "")
        )
    }

    @Test
    fun `split breaks on line boundary when no paragraph break fits`() {
        val text = "line1\nline2\nline3\nline4\nline5"
        val chunks = MarkdownUtil.split(text, 12)

        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 12) }
    }

    @Test
    fun `split breaks on sentence boundary`() {
        val text = "Sentence one. Sentence two. Sentence three. Sentence four."
        val chunks = MarkdownUtil.split(text, 20)

        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 20, "chunk too long: '$it'") }
    }

    @Test
    fun `split breaks on word boundary`() {
        val text = "the quick brown fox jumps over the lazy dog repeatedly"
        val chunks = MarkdownUtil.split(text, 15)

        assertTrue(chunks.size > 1)
        chunks.forEach {
            assertTrue(it.length <= 15)
            assertTrue(!it.startsWith(" ") && !it.endsWith(" "))
        }
    }

    @Test
    fun `split preserves fenced code blocks across chunks`() {
        val text = buildString {
            append("Intro paragraph explaining the code.\n\n")
            append("```kotlin\n")
            repeat(10) { append("val x$it = $it\n") }
            append("```\n\n")
            append("Outro paragraph after the code.")
        }

        val chunks = MarkdownUtil.split(text, 80)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            // Each chunk should have a balanced number of fences
            val fenceCount = Regex("(?m)^```").findAll(chunk).count()
            assertEquals(0, fenceCount % 2, "Unbalanced fences in chunk:\n$chunk")
        }
    }

    @Test
    fun `split reopens fence with language hint`() {
        val text = buildString {
            append("```kotlin\n")
            repeat(20) { append("val variable$it = \"value$it\"\n") }
            append("```")
        }

        val chunks = MarkdownUtil.split(text, 60)

        assertTrue(chunks.size > 1)
        // Every chunk should start with the fence (first one naturally, subsequent reopened)
        chunks.forEach { chunk ->
            assertTrue(chunk.trimStart().startsWith("```kotlin"), "chunk does not start with fence:\n$chunk")
            assertTrue(chunk.trimEnd().endsWith("```"), "chunk does not end with fence:\n$chunk")
        }
    }

    @Test
    fun `split falls back to hard cut when no boundary available`() {
        val text = "a".repeat(50)
        val chunks = MarkdownUtil.split(text, 10)

        assertEquals(5, chunks.size)
        chunks.forEach { assertEquals(10, it.length) }
    }
}
