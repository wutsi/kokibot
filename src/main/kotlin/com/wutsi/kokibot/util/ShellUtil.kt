package com.wutsi.kokibot.util

import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

object ShellUtil {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val DEFAULT_TIMEOUT = 300L
    private val MAX_TIMEOUT = 3600L

    /**
     * Checks if a command exists in the system PATH.
     */
    fun exists(command: String): Boolean {
        val checkCommand = if (isWindows) listOf("where", command) else listOf("which", command)

        return try {
            val process = ProcessBuilder(checkCommand)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()

            // Wait briefly for the check to complete
            val finished = process.waitFor(5, TimeUnit.SECONDS)

            // On Unix, 'which' returns 0 if found, non-zero otherwise
            finished && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Execute a command and returns
     */
    fun exec(command: String, directory: File? = null, timeoutSeconds: Long = -1): ExecResult {
        val builder = ProcessBuilder("sh", "-c", command)
        if (directory != null) {
            builder.directory(directory)
        }
        val process = builder.start()

        // Drain stdout and stderr concurrently to prevent pipe-buffer deadlock.
        // If the process writes more than the OS pipe buffer (~64 KB on Linux,
        // ~8 KB on macOS) without a reader, it blocks — and waitFor() never
        // returns, making the command appear to hang indefinitely.
        val stdoutFuture = drainAsync(process.inputStream)
        val stderrFuture = drainAsync(process.errorStream)

        val timeout = min(MAX_TIMEOUT, if (timeoutSeconds < 0) DEFAULT_TIMEOUT else timeoutSeconds)
        val finished = process.waitFor(timeout, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ExecResult(
                status = -1,
                output = stdoutFuture.get(),
                error = "TIMEOUT. " + (stderrFuture.get() ?: ""),
            )
        }

        val exitValue = process.exitValue()
        return ExecResult(
            status = exitValue,
            output = stdoutFuture.get(),
            error = stderrFuture.get(),
        )
    }

    private fun drainAsync(stream: InputStream): java.util.concurrent.Future<String?> {
        return java.util.concurrent.Executors.newSingleThreadExecutor().submit(
            java.util.concurrent.Callable {
                try {
                    stream.bufferedReader().readText().ifEmpty { null }
                } catch (_: Exception) {
                    null
                }
            }
        )
    }
}

data class ExecResult(
    val status: Int = 0,
    val output: String? = null,
    val error: String? = null,
)
