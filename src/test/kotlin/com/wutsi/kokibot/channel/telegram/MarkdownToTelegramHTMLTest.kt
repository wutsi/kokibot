package com.wutsi.kokibot.channel.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownToTelegramHTMLTest {

    @Test
    fun `bold text`() {
        assertEquals("<b>hello</b>", MarkdownToTelegramHTML.convert("**hello**"))
    }

    @Test
    fun `italic text`() {
        assertEquals("<i>hello</i>", MarkdownToTelegramHTML.convert("*hello*"))
    }

//    @Test
//    fun `strikethrough text`() {
//        assertEquals("<s>hello</s>", MarkdownToTelegramHTML.convert("~~hello~~"))
//    }

    @Test
    fun `inline code`() {
        assertEquals("<code>hello</code>", MarkdownToTelegramHTML.convert("`hello`"))
    }

    @Test
    fun `code block`() {
        assertEquals("<pre><code>hello\n</code></pre>", MarkdownToTelegramHTML.convert("```\nhello\n```"))
    }

    @Test
    fun link() {
        assertEquals(
            """<a href="https://example.com">click here</a>""",
            MarkdownToTelegramHTML.convert("[click here](https://example.com)")
        )
    }

    @Test
    fun h1() {
        assertEquals("<b>Hello</b>", MarkdownToTelegramHTML.convert("# Hello"))
    }

    @Test
    fun h2() {
        assertEquals("<b>Hello</b>", MarkdownToTelegramHTML.convert("## Hello"))
    }

    @Test
    fun h3() {
        assertEquals("<b>Hello</b>", MarkdownToTelegramHTML.convert("### Hello"))
    }

    @Test
    fun `unordered list item`() {
        assertEquals("• item", MarkdownToTelegramHTML.convert("- item"))
    }

    @Test
    fun `plain text is unchanged`() {
        assertEquals("hello world", MarkdownToTelegramHTML.convert("hello world"))
    }

    @Test
    fun `empty string`() {
        assertEquals("", MarkdownToTelegramHTML.convert(""))
    }

    @Test
    fun `mixed formatting`() {
        assertEquals(
            "<b>bold</b> and <i>italic</i>",
            MarkdownToTelegramHTML.convert("**bold** and *italic*")
        )
    }

    @Test
    fun `returns empty string for empty input`() {
        kotlin.test.assertEquals("", MarkdownToTelegramHTML.convert(""))
    }
}
