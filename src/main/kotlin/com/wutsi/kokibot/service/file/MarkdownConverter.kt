package com.wutsi.kokibot.service.file

import com.wutsi.kokibot.util.HtmlUtil
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class MarkdownConverter(
    private val textExtractorFactory: TextExtractorFactory = TextExtractorFactory(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarkdownConverter::class.java)
    }

    fun convert(file: File, contentType: String): String {
        return when {
            contentType.startsWith("text/html") -> pandoc(file) ?: html2md(file)

            contentType.startsWith("text/") -> txt2md(file)

            contentType.startsWith("application/json") -> txt2md(file)

            contentType.startsWith("application/pdf") -> bin2md(file, contentType)

            else -> pandoc(file) ?: bin2md(file, contentType)
        }
    }

    private fun html2md(file: File): String {
        val html = file.readText()
        return HtmlUtil.toMarkdown(html)
    }

    private fun txt2md(file: File): String {
        return file.readText()
    }

    private fun pandoc(file: File): String? {
        // Markdown conversion is best done with pandoc, if available
        if (!ShellUtil.exists("pandoc")) {
            return null
        }

        // Pandoc conversion
        val output = File.createTempFile(file.name, ".md")
        LOGGER.debug("Converting from {} to {} with pandoc", file, output)
        val result = ShellUtil.exec("pandoc ${file.absolutePath} -o ${output.absolutePath}")
        return if (result.status == 0) {
            output.readText()
        } else {
            LOGGER.warn("Conversion from $file to $output failed. Error=${result.error}")
            null
        }
    }

    private fun bin2md(file: File, contentType: String): String {
        val textExtractor = textExtractorFactory.create(contentType)
        return textExtractor.extract(file)
    }
}
