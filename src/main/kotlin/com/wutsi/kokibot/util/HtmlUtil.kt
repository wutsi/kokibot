package com.wutsi.kokibot.util

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
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

    private fun isEmpty(html: String): Boolean {
        return Jsoup.parse(html).body().text().trim().isEmpty()
    }
}
