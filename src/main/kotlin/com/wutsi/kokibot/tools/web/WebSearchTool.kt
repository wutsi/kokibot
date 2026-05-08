package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.net.URLEncoder

class WebSearchTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSearchTool::class.java)
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
        const val URL_PREFIX = "https://duckduckgo.com/html/?q="
        const val NAME = "web_search"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Performs real-time information and internet searches",
        parameters = listOf(
            ToolParameter(
                name = "query",
                description = "The search query",
                type = ToolParameterType.STRING,
                required = true
            )
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val query = arguments["query"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: query")

        LOGGER.info("Web Search: $query")
        val url = URL_PREFIX + URLEncoder.encode(query, "UTF-8")

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .followRedirects(true)
            .get()

        val sb = StringBuffer()
        val results = doc.select(".result")
        var i = 0
        if (results.isEmpty()) {
            sb.append("No results found for the query: $query")
        } else {
            sb.append("${results.size} result(s) found")
            results.forEach { result ->
                val title = result.select(".result__title").text()
                val link = result.select(".result__a").attr("abs:href")
                val snippet = result.select(".result__snippet").text()

                sb.append("Result #${++i}\n")
                sb.append("- Title: ").append(title).append("\n")
                sb.append("- Link: ").append(link).append("\n")
                sb.append("- Snippet: ").append(snippet).append("\n\n")
            }
        }

        return sb.toString()
    }
}
