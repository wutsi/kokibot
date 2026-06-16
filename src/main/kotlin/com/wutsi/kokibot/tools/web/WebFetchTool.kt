package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.UnsupportedMimeTypeException
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Arrays
import java.util.concurrent.TimeUnit

class WebFetchTool(private val maxLength: Int = MAX_FILE_SIZE) : Tool {
    private class FileTooLargeException(message: String) : RuntimeException(message)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebFetchTool::class.java)

        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
        const val NAME = "web_fetch"
        const val BUFFER_SIZE = 1024 * 1024 // 1M
        const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Fetches the content of a URL and converts it to Markdown.
            If the file is too large, it will be truncated to the specified maximum length (if provided).
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "url",
                description = "URL of the web page to fetch",
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
            LOGGER.warn("File too large from URL: {}", url, ex)
            return "Cannot fetch $url. ${ex.message}"
        } catch (ex: Exception) {
            LOGGER.warn("Failed to fetch content from URL: {}", url, ex)
            return "Failed to fetch content from $url. Error= ${ex.message}"
        }
    }

    private fun fetch(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Invalid URL: $url"
        }

        val client = OkHttpClient.Builder()
            .protocols(Arrays.asList(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                LOGGER.error("Failed to fetch content from: {}", url)
                return "Failed to fetch content from $url"
            }

            // Reject up-front if the server advertises a content length larger than maxLength
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            if (contentLength != null && contentLength > maxLength) {
                throw FileTooLargeException(
                    "The file is too large. It exceeds maximum allowed size ($maxLength bytes)"
                )
            }

            // Download
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            val file = download(url, response, contentType)

            // Extract text
            val converter = MarkdownConverter(fileService = context.fileService)
            return extractMetadata(file) + converter.convert(file, contentType).take(maxLength)
        }
    }

    private fun download(url: String, response: okhttp3.Response, contentType: String): File {
        val extension = when {
            contentType.startsWith("text/html") -> "html"
            contentType.startsWith("text/xml") -> "xml"
            contentType.startsWith("text/plain") -> "txt"
            contentType.startsWith("application/json") -> "json"
            contentType.startsWith("application/pdf") -> "pdf"
            contentType.startsWith("application/msword") -> ".doc"
            contentType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document") -> "docx"
            contentType.startsWith("application/vnd.ms-excel") -> "xls"
            contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") -> "xlsx"
            else -> "bin" // Default fallback
        }

        val file = context.fileService.createTempFile("web_fetch", ".$extension")
        try {
            response.body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_FILE_SIZE) {
                            throw FileTooLargeException(
                                "File size exceeds maximum allowed size (${maxLength / (1024 * 1024)} MB)"
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (ex: FileTooLargeException) {
            file.delete()
            throw ex
        }
        LOGGER.debug("{} downloaded to {}. Size={}", url, file.absolutePath, file.length())
        return file
    }

    private fun extractMetadata(file: File): String {
        if (!file.name.endsWith(".html")) {
            return ""
        }

        try {
            val doc = Jsoup.parse(file, "UTF-8")
            val title = doc.title()
            val image = doc.selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?: "N/A"

            return "Title: $title\nImage: $image\n\n"
        } catch (_: Exception) {
            return ""
        }
    }
}
