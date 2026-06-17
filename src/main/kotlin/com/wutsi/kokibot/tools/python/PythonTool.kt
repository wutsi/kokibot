package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeoutException

class PythonTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PythonTool::class.java)

        const val NAME = "python"
        const val DEFAULT_TIMEOUT_SECONDS = 3600L
    }

    private lateinit var context: com.wutsi.kokibot.Context

    override fun init(config: Map<*, *>, context: com.wutsi.kokibot.Context) {
        super.init(config, context)
        this.context = context
    }

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return "Running python code"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Execute a python file and return the result",
        parameters = listOf(
            ToolParameter(
                name = "path",
                description = "Path to the Python file to execute",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "working_dir",
                description = "Working directory for the Python code execution",
                type = ToolParameterType.STRING,
                required = false
            )
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val path = arguments["path"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: code")
        val workingDir = arguments["working_dir"]?.toString()
            ?.ifEmpty { null }
            ?.let { dir -> File(dir) }
        val timeout = DEFAULT_TIMEOUT_SECONDS

        try {
            return exec(path, workingDir, timeout)
        } catch (ex: TimeoutException) {
            LOGGER.warn("Python code execution timed out after $timeout seconds: $path", ex)
            return "TIMEOUT. Execution timed out after $timeout seconds"
        } catch (ex: Exception) {
            LOGGER.warn("Error executing Python code: $path", ex)
            return "FAILURE. ${ex.message}"
        }
    }

    private fun exec(code: String, workingDir: File?, timeoutSeconds: Long): String {
        val result = ShellUtil.exec("python3 $code", workingDir, timeoutSeconds)
        return if (result.status == 0) {
            result.output ?: ""
        } else {
            result.error?.let { error -> "FAILURE. $error" }
                ?: "FAILURE. Exit code ${result.status})"
        }
    }
}
