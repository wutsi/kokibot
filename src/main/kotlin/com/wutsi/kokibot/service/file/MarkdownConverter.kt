package com.wutsi.kokibot.service.file

import com.wutsi.kokibot.service.FileService
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class MarkdownConverter(
    private val textExtractorFactory: TextExtractorFactory = TextExtractorFactory(),
    private val fileService: FileService = FileService(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarkdownConverter::class.java)
    }

    fun convert(file: File, contentType: String): String {
        val md = when {
            contentType.startsWith("text/html") -> markitdown(file) ?: pandoc(file)

            contentType.startsWith("text/") -> file.readText()

            contentType.startsWith("application/json") -> file.readText()

            contentType.startsWith("application/pdf") -> markitdown(file) ?: pandoc(file)

            else -> markitdown(file) ?: pandoc(file)
        }

        return md ?: default(file, contentType)
    }

    private fun pandoc(file: File): String? {
        return exec("pandoc", file)
    }

    private fun markitdown(file: File): String? {
        return exec("markitdown", file)
    }

    private fun exec(tool: String, file: File): String? {
        // Check availability
        if (!ShellUtil.exists(tool)) {
            return null
        }

        // Conversion
        val output = fileService.createTempFile(file.nameWithoutExtension, ".md")
        LOGGER.debug("Converting from {} to {} with {}", file, output, tool)
        val result = ShellUtil.exec("$tool ${file.absolutePath} -o ${output.absolutePath}")
        return if (result.status == 0) {
            output.readText()
        } else {
            LOGGER.warn("Conversion from $file to $output failed. Error=${result.error}")
            null
        }
    }

    private fun default(file: File, contentType: String): String {
        val textExtractor = textExtractorFactory.create(contentType)
        return textExtractor.extract(file)
    }
}
