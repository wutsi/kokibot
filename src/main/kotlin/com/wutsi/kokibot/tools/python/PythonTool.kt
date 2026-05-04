package com.wutsi.kokibot.tools.python

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.MapUtil
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.EnvironmentAccess
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.io.IOAccess
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PythonTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PythonTool::class.java)

        const val NAME = "python"
        const val LANGUAGE_ID = "python"
        const val DEFAULT_TIMEOUT_SECONDS = 60L
        const val MAX_TIMEOUT_SECONDS = 3600L
        const val MIN_TIMEOUT_SECONDS = 1L
    }

    private lateinit var context: com.wutsi.kokibot.Context

    override fun init(config: Map<*, *>, context: com.wutsi.kokibot.Context) {
        super.init(config, context)
        this.context = context
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
            ToolParameter(
                name = "timeout",
                description = """
                Optional timeout in seconds (default: 300, max: 3600).
                 - For atomic tool calculation, it should up to 60s (The default value if not provided).
                 - For complex code execution, it can be up to 300s.
                 - For long-running code, it can be up to 1800s (30min).
                 - If the code execution exceeds the timeout, it will be terminated and an error message will be returned.
                """.trimIndent(),
                type = ToolParameterType.INTEGER,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val code = arguments["code"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: code")
        val timeout = (MapUtil.toLong("timeout", arguments) ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        try {
            return exec(code, timeout)
        } catch (ex: TimeoutException) {
            LOGGER.warn("Python code execution timed out after $timeout seconds: $code", ex)
            return "Error executing Python code: execution timed out after $timeout seconds"
        } catch (ex: Exception) {
            LOGGER.warn("Error executing Python code: $code", ex)
            return "Error executing Python code: ${ex.message}"
        }
    }

    private fun exec(code: String, timeoutSeconds: Long): String {
        val file = File.createTempFile("koki-bot-tool-python-", ".txt")
        val os = FileOutputStream(file)
        val py = createPythonContext(os)

        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "koki-bot-python-exec").apply { isDaemon = true }
        }
        try {
            val future = executor.submit { py.eval(LANGUAGE_ID, code) }
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (ex: TimeoutException) {
                future.cancel(true)
                // Forcibly cancel the running guest script; this interrupts eval
                py.close(true)
                throw ex
            } catch (ex: java.util.concurrent.ExecutionException) {
                throw ex.cause ?: ex
            }
            os.flush()
            return file.readText()
        } finally {
            try {
                py.close()
            } catch (_: Exception) {
                // Already closed (e.g. cancelled) — ignore
            }
            try {
                os.close()
            } catch (_: Exception) {
                // ignore
            }
            executor.shutdownNow()
            file.delete()
        }
    }

    private fun createPythonContext(os: OutputStream): Context {
        return Context.newBuilder(LANGUAGE_ID)
            .option("engine.WarnInterpreterOnly", "false")
            .allowIO(IOAccess.ALL)
            .allowHostAccess(HostAccess.NONE) // Network: DENIED — no host interop means no Java sockets exposed to Python
            .allowPolyglotAccess(PolyglotAccess.NONE) // System commands: DENIED — block subprocess.* / os.system / os.exec*
            .allowCreateProcess(false)
            .allowCreateThread(false) // JVM internals: DENIED — no host class lookup, no class loading, no native calls
            .allowHostClassLoading(false)
            .allowHostClassLookup { _ -> false }
            .allowNativeAccess(false)
            .allowEnvironmentAccess(EnvironmentAccess.NONE)
            .out(os)
            .err(os)
            .currentWorkingDirectory(File(context.home, "workspace").toPath())
            .build()
    }
}
