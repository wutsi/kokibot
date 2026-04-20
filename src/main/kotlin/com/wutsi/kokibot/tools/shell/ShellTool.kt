package com.wutsi.kokibot.tools.shell

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class ShellTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ShellTool::class.java)
        const val ERROR_FORBIDDEN = "Forbidden! You are not allowed to run this command for security reasons."
        const val NAME = "shell"
    }

    private val forbiddenPatterns = listOf(
        "sudo",
        "rm -rf",
        "chmod",
        "chown",
        "> /etc/"
    )

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Run a shell command and return the output",
        parameters = listOf(
            ToolParameter(
                name = "command",
                description = "Command to run",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "directory",
                description = "Working directory (optional)",
                type = ToolParameterType.STRING,
                required = false
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val command = arguments["command"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: command")
        val directory = arguments["directory"]?.toString()?.ifEmpty { null }

        try {
            return "Running command: $command\n" + exec(command, directory)
        } catch (ex: Throwable) {
            LOGGER.warn("Command failed: $command", ex)
            return "Command failed. ${ex.message}"
        }
    }

    private fun exec(command: String, directory: String?): String {
        if (isForbidden(command)) {
            return ERROR_FORBIDDEN
        }

        val result = ShellUtil.exec(command, directory?.let { File(directory) })
        if (result.status == 0) {
            return result.output ?: "Success"
        } else {
            LOGGER.error("Command failed: $command\n${result.error}")
            return result.error ?: "Error. exit code=${result.status}"
        }
    }

    private fun isForbidden(command: String): Boolean {
        return forbiddenPatterns.any { command.contains(it) }
    }
}
