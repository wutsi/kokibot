package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.UnsupportedMimeTypeException
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

class WebFetchTool : Tool {
    private class FileTooLargeException(message: String) : RuntimeException(message)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebFetchTool::class.java)

        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
        const val NAME = "web_fetch"
        const val BUFFER_SIZE = 1024 * 1024 // 1M
        const val DEFAULT_MAX_LENGTH = 1 * 1024 * 1024 // 1MB
        const val MIN_FILE_SIZE = 25 * 1024 // 25K
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
            ),
            ToolParameter(
                name = "max_length",
                description = "Maximum length of the returned content (optional)",
                type = ToolParameterType.INTEGER,
                required = false,
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val url = arguments["url"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: url")

        val maxLength = (MapUtil.toInt("max_length", arguments) ?: DEFAULT_MAX_LENGTH)
            .coerceIn(MIN_FILE_SIZE, MAX_FILE_SIZE)

        try {
            val content = fetch(url, maxLength)
            return "BEGIN URL CONTENT - $url\n\n" +
                content +
                "\n\nEND URL CONTENT"
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

    private fun fetch(url: String, maxLength: Int): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Invalid URL: $url"
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                LOGGER.error("Failed to fetch content from: {}", url)
                return "Failed to fetch content from $url"
            }

            // Reject up-front if the server advertises a content length larger than MAX_FILE_SIZE
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_FILE_SIZE) {
                throw FileTooLargeException(
                    "File size ($contentLength bytes) exceeds maximum allowed size ($MAX_FILE_SIZE bytes)"
                )
            }

            // Download
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            val file = download(url, response, contentType)

            // Extract text
            val converter = MarkdownConverter(fileService = context.fileService)
            return converter.convert(file, contentType).take(maxLength)
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
                                "File size exceeds maximum allowed size (${MAX_FILE_SIZE / (1024 * 1024)} MB)"
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
}
