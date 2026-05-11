package com.wutsi.kokibot.channel.telegram

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownToTelegramHTML {
    private val options = MutableDataSet().apply {
        set(HtmlRenderer.SOFT_BREAK, "<br/>")
    }

    private val parser = Parser.builder(options).build()
    private val renderer = HtmlRenderer.builder(options).build()

    // Regex helpers - case-insensitive, allow optional attributes
    private fun open(tag: String) = Regex("(?i)<\\s*$tag\\b[^>]*>")
    private fun close(tag: String) = Regex("(?i)</\\s*$tag\\s*>")
    private fun void(tag: String) = Regex("(?i)<\\s*$tag\\b[^>]*/?\\s*>")

    fun convert(markdown: String): String {
        val document = parser.parse(markdown)
        val html = renderer.render(document)

        // Telegram specific cleanup.
        // Telegram doesn't support <div>, <p>, <ol>, <ul>, <h1>..<h6>, <hr/> tags.
        // It only likes <b>, <i>, <code>, <a>, and <s>.
        var result = html
            .replace(open("p"), "")
            .replace(close("p"), "\n")
            .replace(void("br"), "\n")
            .replace(open("ul"), "")
            .replace(close("ul"), "")
            .replace(open("ol"), "")
            .replace(close("ol"), "")
            .replace(open("li"), "• ")
            .replace(close("li"), "\n")
            .replace(open("strong"), "<b>")
            .replace(close("strong"), "</b>")
            .replace(open("em"), "<i>")
            .replace(close("em"), "</i>")
            .replace(void("hr"), "\n\n")
            .replace(open("del"), "<s>")
            .replace(close("del"), "</s>")
            .replace(open("strike"), "<s>")
            .replace(close("strike"), "</s>")

        for (i in 1..6) {
            result = result
                .replace(open("h$i"), "<b>")
                .replace(close("h$i"), "</b>\n")
        }

        return result
            .replace(Regex("\\R+"), "\n") // Should be the last!
            .trim()
    }
}
