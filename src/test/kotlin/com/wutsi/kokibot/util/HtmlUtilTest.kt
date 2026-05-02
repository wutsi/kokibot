package com.wutsi.kokibot.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlUtilTest {
    @Test
    fun empty() {
        assertEquals("", HtmlUtil.toMarkdown(""))
    }

    @Test
    fun toMarkdown() {
        val html = """
            <h1>Title</h1>
            <p>This is a <b>paragraph</b>.</p>
            <ul>
                <li>Item 1</li>
                <li>Item 2</li>
            </ul>
        """.trimIndent()

        val expected = """
            Title
            =====

            This is a **paragraph**.

            * Item 1
            * Item 2

        """.trimIndent()

        assertEquals(expected, HtmlUtil.toMarkdown(html))
    }

    @Test
    fun fromMarkdown() {
        val markdown = """
            Title
            =====

            This is a **paragraph**.

            * Item 1
            * Item 2
        """.trimIndent()

        val expected = """
            <h1>Title</h1>
            <p>This is a <strong>paragraph</strong>.</p>
            <ul>
            <li>Item 1</li>
            <li>Item 2</li>
            </ul>

        """.trimIndent()

        assertEquals(expected, HtmlUtil.fromMarkdown(markdown))
    }
}
