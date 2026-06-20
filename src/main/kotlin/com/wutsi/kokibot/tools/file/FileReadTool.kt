package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.web.WebFetchTool
import org.springframework.http.MediaTypeFactory

class FileReadTool(private val maxLength: Int = WebFetchTool.MAX_FILE_SIZE) : AbstractFileTool() {
    companion object {
        const val NAME = "file_read"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Read the content of a file and return its text version so that it can be interpreted by LLM.
            This tool supports:
            - Text files (.txt, .md, .csv, etc.)
            - PDF - Return as markdown if possible, otherwise return as text
            - Office documents (.docx, .xlsx, .pptx, xls, doc) - Return as markdown if possible, otherwise return as text.

            It can read files with a maximum size of ${maxLength / (1024 * 1024)} Mb. If the file content will be truncated
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to read",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return if (accessingMemory(toolCalls)) {
            "Reading memory"
        } else if (toolCalls.size == 1) {
            "Reading " + toolCalls[0].arguments["path"]?.toString()
        } else {
            "Reading ${toolCalls.size} files"
        }
    }

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")

        return try {
            read(path)
        } catch (ex: Throwable) {
            "Failed to read file. Error=${ex.message}"
        }
    }

    private fun read(path: String): String {
        val file = java.io.File(path)
        if (!file.exists()) {
            return "File not found: $path"
        }
        if (!file.isFile) {
            return "Not a file: $path"
        }
        if (!file.canRead()) {
            return "File is not readable: $path"
        }

        val extension = file.extension.lowercase()
        val contentType = MediaTypeFactory.getMediaType(file.name)
            .map { it.toString() }
            .orElse(null)

        val content = when {
            contentType == null -> if (extension in listOf("md")) {
                file.readText()
            } else {
                return "Cannot read $path. Unsupported file type: $extension"
            }

            contentType.startsWith("text/") || contentType.equals("application/json") -> file.readText()

            else -> try {
                val converter = MarkdownConverter(fileService = context.fileService)
                converter.convert(file, contentType).take(maxLength)
            } catch (ex: Throwable) {
                return "FAILURE. " + (ex.message ?: "Failed to convert $path to markdown")
            }
        }

        return "<file-content>\n" + content.take(maxLength) + "\n</file-content>"
    }
}
