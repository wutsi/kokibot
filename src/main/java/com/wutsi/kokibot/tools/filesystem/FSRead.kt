package com.wutsi.kokibot.tools.filesystem

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class FSRead : Tool {
    companion object {
        const val NAME = "fs_read"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Read the content of a file in the file system.",
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to read.",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")
        return read(path)
    }

    private fun read(path: String): String {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) {
                "File not found: $path"
            } else if (!file.isFile) {
                "Not a file: $path"
            } else {
                file.readText()
            }
        } catch (ex: Exception) {
            "Error reading file: ${ex.message}"
        }
    }
}
