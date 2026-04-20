package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.tools.web.WebFetchTool.Companion.DEFAULT_MAX_LENGTH
import com.wutsi.kokibot.util.MapUtil

class FileRead : Tool {
    companion object {
        const val NAME = "file_read"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Read the content of a file and return it as Markdown.
            If the file is too large, it will be truncated to the specified maximum length (if provided)."

            This tool can only read text files: .txt, .md, .json, .xml, .csv, .log, .yaml, .yml, .md, .html, .htm, .css, .js, .java, .kt, .py, .go, .rb, .php etc.

            For binary files, use available skills to convert them to Markdown format before reading with this tool.
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to read",
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
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")
        val maxLength = MapUtil.toInt("max_length", arguments)

        return try {
            read(path, maxLength ?: DEFAULT_MAX_LENGTH)
        } catch (ex: Throwable) {
            "Failed to read file. Error=${ex.message}"
        }
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

        return "BEGIN FILE CONTENT: $path\n\n" +
            file.readText().take(maxLength) +
            "\n\nEND FILE CONTENT"
    }
}
