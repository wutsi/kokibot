package com.wutsi.kokibot.channel.telegram

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownToTelegramHTML {
    private val options = MutableDataSet().apply {
        // Basic configuration to keep HTML clean
        set(HtmlRenderer.SOFT_BREAK, "<br/>")
    }

    private val parser = Parser.builder(options).build()
    private val renderer = HtmlRenderer.builder(options).build()

    fun convert(markdown: String): String {
        // 1. Parse Markdown to Document
        val document = parser.parse(markdown)

        // 2. Render to HTML
        val html = renderer.render(document)

        // 3. Telegram specific cleanup
        // Telegram doesn't support <div>, <p>, <ol>, or <ul> tags.
        // It only likes <b>, <i>, <code>, <a>, and <s>.
        return html
            .replace("<p>", "")
            .replace("<br/>", "\n")
            .replace("</p>", "\n")
            .replace("<ul>", "")
            .replace("</ul>", "")
            .replace("<ol>", "")
            .replace("</ol>", "")
            .replace("<li>", "• ")
            .replace("</li>", "\n")
            .replace("<h1>", "<b>")
            .replace("</h1>", "</b>\n")
            .replace("<h2>", "<b>")
            .replace("</h2>", "</b>\n")
            .replace("<h3>", "<b>")
            .replace("</h3>", "</b>\n")
            .replace("<strong>", "<b>")
            .replace("</strong>", "</b>")
            .replace("<em>", "<i>")
            .replace("</em>", "</i>")
            .replace("<del>", "<s>")
            .replace("</del>", "</s>")
            .replace("<strike>", "<s>")
            .replace("</strike>", "</s>")
            .replace(Regex("\\R+"), "\n") // Should be the last!
            .trim()
    }
}
