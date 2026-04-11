package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.HtmlUtil
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.net.URL

class WebFetchTool : Tool {
    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
        const val NAME = "web_fetch"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Fetches the content of a web page and converts it to markdown.",
        parameters = listOf(
            ToolParameter(
                name = "url",
                description = "URL of the web page to fetch",
                type = ToolParameterType.STRING,
                required = true
            )
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val url = arguments["url"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: url")

        if (isPdf(url)) {
            return fetchPdf(url)
        } else {
            return fetchHtml(url)
        }
    }

    private fun fetchHtml(url: String): String {
        val html = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .followRedirects(true)
            .get()
            .html()
        return HtmlUtil.toMarkdown(html)
    }

    private fun fetchPdf(url: String): String {
        val content = URL(url).readBytes()
        val doc = Loader.loadPDF(content)
        val stripper = PDFTextStripper()
        stripper.startPage = 1
        stripper.endPage = doc.numberOfPages
        return stripper.getText(doc)
    }

    private fun isPdf(url: String): Boolean {
        return url.lowercase().endsWith(".pdf")
    }
}
