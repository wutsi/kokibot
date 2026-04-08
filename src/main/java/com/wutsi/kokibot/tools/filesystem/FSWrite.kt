package com.wutsi.kokibot.tools.filesystem

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import java.io.File

class FSWrite : Tool {
    companion object {
        const val NAME = "fs_write"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Write content to a file in the file system.",
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to write.",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "content",
                description = "Content to write to the file.",
                type = ToolParameterType.STRING,
                required = false
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")

        val content = arguments["content"]?.toString()?.ifEmpty { null }
        return write(path, content)
    }

    private fun write(path: String, content: String?): String {
        return try {
            val file = File(path)
            file.writeText(content ?: "")
            "File stored: $path"
        } catch (ex: Exception) {
            "Unable to write the file: ${ex.message}"
        }
    }
}
