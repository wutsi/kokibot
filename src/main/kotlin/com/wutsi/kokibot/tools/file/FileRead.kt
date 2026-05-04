package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.web.WebFetchTool.Companion.MAX_FILE_SIZE
import org.springframework.http.MediaTypeFactory

class FileRead : Tool {
    companion object {
        const val NAME = "file_read"
    }

    private lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Read the content of a file and return its text version so that it can be interpreted by LLM.
            This tool supports:
            - Text files (.txt, .md, .csv, etc.)
            - PDF - Return as markdown if possible, otherwise return as text
            - Office documents (.docx, .xlsx, .pptx, xls, doc) - Return as markdown if possible, otherwise return as text
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

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")

        val result = try {
            read(path, MAX_FILE_SIZE)
        } catch (ex: Throwable) {
            "Failed to read file. Error=${ex.message}"
        }
        return "BEGIN FILE CONTENT: $path\n\n" +
            result +
            "\n\nEND FILE CONTENT: $path"
    }

    private fun read(path: String, maxLength: Int): String {
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
                return converter.convert(file, contentType)
            } catch (ex: Throwable) {
                return "Failed to read text from $path. Error=${ex.message}"
            }
        }
        return content.take(maxLength)
    }
}
