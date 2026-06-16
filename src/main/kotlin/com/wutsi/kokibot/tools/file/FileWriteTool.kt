package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import java.io.File

class FileWriteTool : AbstractFileTool() {
    companion object {
        const val NAME = "file_write"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Write a content into a file. The content should be in text format",
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to read",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "content",
                description = "Content of the file to write. If content is not provided, it will be treated as empty string.",
                type = ToolParameterType.STRING,
                required = false,
            ),
            ToolParameter(
                name = "overwrite",
                description = """
                    Overwrite the file if it already exists.
                    If not provided, it will be treated as false, which means the file will not be overwritten and an error message will be returned if the file already exists.
                """.trimIndent(),
                type = ToolParameterType.BOOLEAN,
                required = false,
            ),
        )
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return if (accessingMemory(toolCalls)) {
            "Saving memory"
        } else if (toolCalls.size == 1) {
            "Saving " + toolCalls[0].arguments["path"]?.toString()
        } else {
            "Saving ${toolCalls.size} files"
        }
    }

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")
        val content = MapUtil.toString("content", arguments) ?: ""
        val overwrite = MapUtil.toBoolean("overwrite", arguments) ?: false

        return try {
            write(path, content, overwrite)
        } catch (ex: Throwable) {
            "Failed to read file. Error=${ex.message}"
        }
    }

    private fun write(path: String, content: String, overwrite: Boolean): String {
        val file = File(path)
        if (file.exists()) {
            if (!file.isFile) {
                return "Not a file: $path"
            }
            if (!overwrite) {
                return "File already exists: $path"
            }
        } else {
            file.parentFile?.mkdirs()
        }
        file.writeText(content)
        return "SUCCESS. File saved."
    }
}
