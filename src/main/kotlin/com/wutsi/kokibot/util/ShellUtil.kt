package com.wutsi.kokibot.util

import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object ShellUtil {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

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

        if (timeoutSeconds <= 0) {
            process.waitFor()
        } else {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ExecResult(
                    status = -1,
                    output = toString(process.inputStream),
                    error = toString(process.errorStream),
                )
            }
        }

        val exitValue = process.exitValue()
        return ExecResult(
            status = exitValue,
            output = toString(process.inputStream),
            error = toString(process.errorStream),
        )
    }

    private fun toString(stream: InputStream): String? {
        return try {
            stream.bufferedReader().readText().ifEmpty { null }
        } catch (ex: Exception) {
            null
        }
    }
}

data class ExecResult(
    val status: Int = 0,
    val output: String? = null,
    val error: String? = null,
)
