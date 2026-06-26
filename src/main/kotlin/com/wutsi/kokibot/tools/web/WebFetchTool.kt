package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.UnsupportedMimeTypeException
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.FileTooLargeException
import com.wutsi.kokibot.util.URLUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

class WebFetchTool(private val maxLength: Int = URLUtil.MAX_FILE_SIZE) : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebFetchTool::class.java)
        const val NAME = "web_fetch"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Fetches the content of a LINK and converts it to Markdown.
            If the file is too large, it will be truncated to the specified maximum length (if provided).
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "url",
                description = "LINK of the web page to fetch",
                type = ToolParameterType.STRING,
                required = true
            )
        )
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        val location = if (toolCalls.size == 1) {
            toolCalls[0].arguments["url"]
        } else {
            toolCalls.mapNotNull { tool -> tool.arguments["url"]?.toString() }
                .map { url -> URL(url).host }
                .distinct()
                .joinToString(", ")
        }
        return "Reading online from $location"
    }

    override fun exec(arguments: Map<*, *>): String {
        val url = arguments["url"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: url")

        try {
            val content = fetch(url)
            val file = context.fileService.createTempFile("web_fetch_result", ".md")
            file.writeText(content)
            return "Content fetched from $url and saved to ${file.absolutePath}"
        } catch (ex: UnsupportedMimeTypeException) {
            LOGGER.warn("Cannot extract the content from : {}", url, ex)
            return "Cannot extract the content from $url. Error= ${ex.message}"
        } catch (ex: FileTooLargeException) {
            LOGGER.warn("File too large from LINK: {}", url, ex)
            return "Cannot fetch $url. ${ex.message}"
        } catch (ex: Exception) {
            LOGGER.warn("Failed to fetch content from LINK: {}", url, ex)
            return "Failed to fetch content from $url. Error= ${ex.message}"
        }
    }

    private fun fetch(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Invalid LINK: $url"
        }

        val file = URLUtil.fetch(URL(url))
        val content = convert(file)
        return content.take(maxLength)
    }

    private fun convert(file: File): String {
        return MarkdownConverter(fileService = context.fileService).convert(file)
    }
}
