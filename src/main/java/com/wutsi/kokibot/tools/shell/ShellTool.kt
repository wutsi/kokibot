package com.wutsi.kokibot.tools.shell

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ShellTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ShellTool::class.java)
        const val ERROR_FORBIDDEN = "Forbidden! You are not allowed to run this command for security reasons."
        const val ERROR_TIMEOUT = "Error: Execution timed out."
        const val NAME = "shell"
        const val TIMEOUT = 5
    }

    private lateinit var root: File
    private var timeout: Int = TIMEOUT
    val forbiddenPatterns = listOf(
        "sudo",
        "rm -rf",
        "chmod",
        "chown",
        "> /etc/"
    )

    override fun init(config: Map<*, *>, context: Context) {
        timeout = MapUtil.toInt("timeout", config) ?: TIMEOUT

        val rootPath = config["root-directory"]?.toString()?.ifEmpty { null }
            ?: File(context.home, "workspace").absolutePath
        root = File(rootPath)
        LOGGER.info("Shell working directory: ${root.absolutePath}")
        if (!root.exists()) {
            root.mkdirs()
        }
    }

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
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val command = arguments["command"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: command")

        try {
            return exec(command)
        } catch (ex: Throwable) {
            LOGGER.warn("Command failed: $command", ex)
            return "Command failed. ${ex.message}"
        }
    }

    private fun exec(command: String): String {
        if (isForbidden(command)) {
            return ERROR_FORBIDDEN
        }

        val process = ProcessBuilder("sh", "-c", command)
            .directory(root)
            .start()

        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ERROR_TIMEOUT
        }

        return if (process.exitValue() == 0) {
            process.inputStream.bufferedReader().readText()
        } else {
            process.errorStream.bufferedReader().readText()
        }
    }

    private fun isForbidden(command: String): Boolean {
        return forbiddenPatterns.any { command.contains(it) }
    }
}
