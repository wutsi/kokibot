package com.wutsi.kokibot.util

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.wutsi.kokibot.util.html.HtmlSanitizer
import org.jsoup.Jsoup

object HtmlUtil {
    private val sanitizer = HtmlSanitizer()

    fun toMarkdown(html: String): String {
        val content = sanitizer.sanitize(html)
        if (isEmpty(content)) {
            return ""
        } else {
            return FlexmarkHtmlConverter.builder().build().convert(content)
        }
    }

    fun fromMarkdown(markdown: String): String {
        val options = MutableDataSet()
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    private fun isEmpty(html: String): Boolean {
        return Jsoup.parse(html).body().text().trim().isEmpty()
    }
}
