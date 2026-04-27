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

    // Matches a GFM-style table: a header row, a separator row, then 0+ body rows.
    private val tableRegex = Regex(
        pattern = "(?m)^[ \\t]*\\|.*\\|[ \\t]*\\R" + // header row
            "[ \\t]*\\|[\\s:|-]+\\|[ \\t]*\\R" + // separator row
            "(?:[ \\t]*\\|.*\\|[ \\t]*(?:\\R|\\z))*" // body rows
    )

    fun convert(markdown: String): String {
        // 1. Extract tables and replace with placeholders BEFORE parsing.
        val tables = mutableListOf<String>()
        val prepared = tableRegex.replace(markdown) { match ->
            val idx = tables.size
            tables += match.value.trimEnd()
            "\n\n@@TABLE_$idx@@\n\n"
        }

        // 2. Parse Markdown to Document
        val document = parser.parse(prepared)

        // 3. Render to HTML
        val html = renderer.render(document)

        // 4. Telegram specific cleanup
        // Telegram doesn't support <div>, <p>, <ol>, <ul>, <h1>..<h6>, <hr/>  tags.
        // It only likes <b>, <i>, <code>, <a>, and <s>.
        var result = html
            .replace("<p>", "")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
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
            .replace("<h4>", "<b>")
            .replace("</h4>", "</b>\n")
            .replace("<h5>", "<b>")
            .replace("</h5>", "</b>\n")
            .replace("<h6>", "<b>")
            .replace("</h6>", "</b>\n")
            .replace("<strong>", "<b>")
            .replace("</strong>", "</b>")
            .replace("<em>", "<i>")
            .replace("</em>", "</i>")
            .replace("<hr/>", "\n\n")
            .replace("<hr />", "\n\n")
            .replace("<del>", "<s>")
            .replace("</del>", "</s>")
            .replace("<strike>", "<s>")
            .replace("</strike>", "</s>")
            .replace(Regex("\\R+"), "\n") // Should be the last!
            .trim()

        // 5. Restore tables wrapped in <pre>...</pre>
        tables.forEachIndexed { idx, table ->
            result = result.replace("@@TABLE_$idx@@", "<pre>\n$table\n</pre>")
        }
        return result.trim()
    }
}
