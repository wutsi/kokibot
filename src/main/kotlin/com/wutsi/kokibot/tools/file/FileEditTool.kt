package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import java.io.File

class FileEditTool : AbstractFileTool() {
    companion object {
        const val NAME = "file_edit"
        const val MIN_SEARCH_LENGTH = 5
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = """
            Edit a file by replacing a block of text with another block of text. The search block should be unique in the file to avoid unintended replacements.
            The search and replace blocks should be provided in their exact form, including whitespace and indentation, to ensure accurate matching.
        """.trimIndent(),
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path of the file to edit",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "search",
                description = "Search block. For safe editing, the search block should be at least $MIN_SEARCH_LENGTH characters long and unique in the file.",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "replace",
                description = "Replacement block",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return if (accessingMemory(toolCalls)) {
            "Updating memory"
        } else {
            "Updating ${toolCalls.size} file" + (if (toolCalls.size > 1) "s" else "") +
                (if (toolCalls.size == 1) ": ${toolCalls[0].arguments["path"]}" else "")
        }
    }

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: path")
        val search = arguments["search"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: search")
        val replace = arguments["replace"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: replace")

        return try {
            edit(path, search, replace)
        } catch (ex: Throwable) {
            "Error: Failed apply the search/replace into $path: ${ex.message}"
        }
    }

    private fun edit(
        path: String,
        search: String,
        replace: String,
    ): String {
        val file = File(path)
        if (!file.exists()) return "FAILURE. File not found."

        val currentContent = file.readText()

        // 1. Validation: Ensure the search block is unique and present
        val occurrences = currentContent.split(search).size - 1

        return when {
            occurrences == 0 -> "FAILURE. Search block not found. Ensure whitespace/indentation matches exactly."
            occurrences > 1 -> "FAILURE. Search block is not unique ($occurrences matches found). Provide more context."
            search.length < MIN_SEARCH_LENGTH -> "FAILURE. Search block too short to be safe. Search block should be at least $MIN_SEARCH_LENGTH characters long."
            else -> {
                // 2. The Swap
                val newContent = currentContent.replace(search, replace)
                file.writeText(newContent)
                "SUCCESS. File updated."
            }
        }
    }
}
