package com.wutsi.kokibot.tools.filesystem

import com.wutsi.kokibot.file.TextExtractorFactory
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.slf4j.LoggerFactory
import org.springframework.http.MediaTypeFactory
import java.io.File

class FSRead(
    private val factory: TextExtractorFactory = TextExtractorFactory()
) : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FSRead::class.java)

        const val NAME = "fs_read"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Read the content of a file and convert it to text. The content will be returned as a string.
            This tool supports text files (e.g. .txt, .csv, .json) and binary files (e.g. .pdf, .docx, .xlsx).
            For binary files, the content will be extracted using the appropriate text extractor based on the file's MIME type.
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "file",
                description = "Path of the file to read.",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val file = arguments["file"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: file")

        try {
            return read(file)
        } catch (ex: Exception) {
            LOGGER.warn("Unable to extract the content of the file ", ex)
            return "Unable to extract the content of $file: ${ex.message}"
        }
    }

    private fun read(file: String): String {
        val f = File(file)
        val mimeType = MediaTypeFactory.getMediaType(f.name)
            .map { it.toString() }
            .orElse("application/octet-stream")

        return if (mimeType == "application/json" || mimeType.startsWith("text/")) {
            f.readText()
        } else {
            factory.create(mimeType).extract(f)
        }
    }
}
