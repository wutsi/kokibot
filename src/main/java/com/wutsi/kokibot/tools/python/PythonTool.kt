package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.graalvm.polyglot.Context
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

class PythonTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PythonTool::class.java)

        const val NAME = "python"
        const val LANGUAGE_ID = "python"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Run a Python code and return the output",
        parameters = listOf(
            ToolParameter(
                name = "code",
                description = "Code to run",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val code = arguments["code"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: code")

        try {
            return exec(code)
        } catch (ex: Exception) {
            LOGGER.warn("Error executing Python code: $code", ex)
            return "Error executing Python code: ${ex.message}"
        }
    }

    private fun exec(code: String): String {
        val file = File.createTempFile("koki-bot-tool-python-", ".txt")
        try {
            val os = FileOutputStream(file)
            os.use {
                val py = Context.newBuilder("python")
                    .option("engine.WarnInterpreterOnly", "false")
                    .allowAllAccess(true)
                    .out(os)
                    .err(os)
                    .build()

                py.use { context ->
                    context.eval(LANGUAGE_ID, code)
                }
            }
            return file.readText()
        } finally {
            file.delete()
        }
    }
}
