package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.EnvironmentAccess
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.io.IOAccess
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
                val py = Context.newBuilder(LANGUAGE_ID)
                    .option("engine.WarnInterpreterOnly", "false")
                    // Filesystem: ALLOWED — full host I/O so Python code can read/write files
                    .allowIO(IOAccess.ALL)
                    // Network: DENIED — no host interop means no Java sockets exposed to Python
                    .allowHostAccess(HostAccess.NONE)
                    .allowPolyglotAccess(PolyglotAccess.NONE)
                    // System commands: DENIED — block subprocess.* / os.system / os.exec*
                    .allowCreateProcess(false)
                    .allowCreateThread(false)
                    // JVM internals: DENIED — no host class lookup, no class loading, no native calls
                    .allowHostClassLoading(false)
                    .allowHostClassLookup { _ -> false }
                    .allowNativeAccess(false)
                    .allowEnvironmentAccess(EnvironmentAccess.NONE)
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
